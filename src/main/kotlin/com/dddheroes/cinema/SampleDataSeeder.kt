package com.dddheroes.cinema

import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.MovieId
import com.dddheroes.cinema.modules.dayschedule.read.getschedulebyid.GetScheduleById
import com.dddheroes.cinema.modules.dayschedule.read.getschedulebyid.GetScheduleByIdResult
import com.dddheroes.cinema.modules.dayschedule.write.createschedule.CreateDaySchedule
import com.dddheroes.cinema.modules.dayschedule.write.schedulescreening.ScheduleScreening
import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.write.confirmseats.ConfirmSeats
import com.dddheroes.cinema.modules.reservations.write.startreservation.StartReservation
import com.dddheroes.cinema.modules.seatsblocking.write.blockseats.BlockSeats
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.sdk.application.CommandResult
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.extensions.kotlin.query
import org.axonframework.queryhandling.QueryGateway
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.*
import kotlin.random.Random

data class SeederConfig(
    val startDate: LocalDate = LocalDate.now().minusDays(7),
    val endDate: LocalDate = LocalDate.now().plusDays(7),
    val movies: List<Movie> = defaultMovies,
    val randomSeed: Long = 42
)

data class Movie(
    val id: MovieId,
    val name: String,
    val durationMinutes: Int,
    val popularity: Double // 0.0 to 1.0 - affects reservation probability
)

private val defaultMovies = listOf(
    Movie(MovieId("avatar-3"), "Avatar 3", 180, 0.9),
    Movie(MovieId("dune-3"), "Dune: Part Three", 165, 0.85),
    Movie(MovieId("inception-2"), "Inception 2", 150, 0.8),
    Movie(MovieId("blade-runner-2099"), "Blade Runner 2099", 140, 0.7),
    Movie(MovieId("marvel-heroes-x"), "Marvel Heroes X", 155, 0.95),
    Movie(MovieId("indie-drama"), "Small Town Stories", 105, 0.3),
    Movie(MovieId("horror-classic"), "The Haunting Returns", 110, 0.6),
    Movie(MovieId("romantic-comedy"), "Love in Paris", 120, 0.5)
)

@Service
@ConditionalOnProperty(name = ["app.sample-data-init.enabled"], havingValue = "true")
class SampleDataSeeder(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(SampleDataSeeder::class.java)
    private val random = Random(42)

    @EventListener(ApplicationReadyEvent::class)
    fun initializeSampleData() {
        logger.info("SampleDataSeeder ApplicationReadyEvent triggered - starting automatic data seeding")
        try {
            val result = seedData()
            logger.info("Automatic data seeding completed successfully: ${result.summary}")
        } catch (e: Exception) {
            logger.error("Failed to initialize sample data during @PostConstruct", e)
        }
    }

    fun seedData(config: SeederConfig = SeederConfig()): DataSeederResult {
        logger.info("Starting cinema data seeding from ${config.startDate} to ${config.endDate}")
        logger.info("Seeding configuration: movies=${config.movies.size}, randomSeed=${config.randomSeed}")
        logger.debug("Movies to be used: ${config.movies.map { "${it.name} (${it.id.raw})" }}")

        val results = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var totalScreenings = 0
        var totalReservations = 0

        // Seed for each day
        var currentDate = config.startDate
        while (!currentDate.isAfter(config.endDate)) {
            logger.info("Processing date: $currentDate")
            try {
                // Check if schedule already exists for this day
                val existingSchedule = queryGateway.query<GetScheduleByIdResult?, GetScheduleById>(
                    GetScheduleById(DayScheduleId(currentDate))
                ).join()

                if (existingSchedule != null) {
                    val skipMessage = "Day $currentDate: Schedule already exists, skipping data seeding"
                    logger.info(skipMessage)
                    results.add(skipMessage)
                } else {
                    val dayResult = seedDayData(currentDate, config.movies)
                    val dayMessage = "Day $currentDate: ${dayResult.screenings} screenings, ${dayResult.reservations} reservations"
                    logger.info(dayMessage)
                    results.add(dayMessage)
                    totalScreenings += dayResult.screenings
                    totalReservations += dayResult.reservations
                }
            } catch (e: Exception) {
                val error = "Failed to seed data for $currentDate: ${e.message}"
                logger.error(error, e)
                errors.add(error)
            }
            currentDate = currentDate.plusDays(1)
        }

        val summary = "Seeded $totalScreenings screenings and $totalReservations reservations across ${config.endDate.toEpochDay() - config.startDate.toEpochDay() + 1} days"
        logger.info("Data seeding completed: $summary")
        if (errors.isNotEmpty()) {
            logger.warn("Seeding completed with ${errors.size} errors")
        }

        return DataSeederResult(
            summary = summary,
            details = results,
            errors = errors,
            totalScreenings = totalScreenings,
            totalReservations = totalReservations
        )
    }

    private fun seedDayData(date: LocalDate, movies: List<Movie>): DaySeederResult {
        logger.debug("Creating day schedule for $date")
        // Create day schedule
        val createDayScheduleCommand = CreateDaySchedule(
            dayScheduleId = DayScheduleId(date),
            openingTime = LocalTime.of(9, 0),
            closingTime = LocalTime.of(21, 0),
            issuedAt = clock.instant()
        )

        when (val result = commandGateway.sendAndWait<CommandResult>(createDayScheduleCommand)) {
            is CommandResult.Failure -> {
                logger.error("Failed to create day schedule for $date: ${result.message}")
                throw RuntimeException("Failed to create day schedule: ${result.message}")
            }
            is CommandResult.Success -> logger.info("Successfully created day schedule for $date")
        }

        // Schedule 3-5 screenings per day
        val numberOfScreenings = random.nextInt(3, 6)
        logger.info("Planning $numberOfScreenings screenings for $date")
        val selectedMovies = movies.shuffled(random).take(numberOfScreenings)
        logger.debug("Selected movies for $date: ${selectedMovies.map { it.name }}")

        val screenings = mutableListOf<ScreeningData>()
        var currentTime = LocalTime.of(10, 0) // Start first screening at 10:00

        selectedMovies.forEachIndexed { index, movie ->
            val screeningId = ScreeningId("screening-${date}-${movie.id.raw}-${UUID.randomUUID().toString().take(8)}")
            val startTime = currentTime
            val endTime = startTime.plusMinutes(movie.durationMinutes.toLong() + 30)

            logger.debug("Attempting to schedule screening ${index + 1}/$numberOfScreenings: ${movie.name} at $startTime-$endTime")

            if (endTime.isBefore(LocalTime.of(21, 0))) {
                val scheduleScreeningCommand = ScheduleScreening(
                    dayScheduleId = DayScheduleId(date),
                    screeningId = screeningId,
                    movieId = movie.id,
                    startTime = startTime,
                    endTime = endTime,
                    occurredAt = clock.instant()
                )

                when (val result = commandGateway.sendAndWait<CommandResult>(scheduleScreeningCommand)) {
                    is CommandResult.Failure -> {
                        logger.warn("Failed to schedule screening '${movie.name}' at $startTime for $date: ${result.message}")
                    }
                    is CommandResult.Success -> {
                        logger.info("Successfully scheduled '${movie.name}' at $startTime-$endTime for $date")
                        screenings.add(ScreeningData(screeningId, movie, startTime, endTime))
                        currentTime = endTime.plusMinutes(15) // 15 min break between screenings
                    }
                }
            } else {
                logger.warn("Skipping screening '${movie.name}' - would end at $endTime (after closing time)")
            }
        }

        logger.info("Successfully scheduled ${screenings.size} screenings for $date")

        // Create press seat blockings for some screenings (premium movies)
        Thread.sleep(2000)
        val premiumScreenings = screenings.filter { it.movie.popularity > 0.8 }
        premiumScreenings.forEach { screening ->
            createPressSeatsBlocking(screening, date)
        }

        // Create reservations for screenings
        var totalReservations = 0
        screenings.forEachIndexed { index, screening ->
            logger.debug("Creating reservations for screening ${index + 1}/${screenings.size}: ${screening.movie.name}")
            val reservationsForScreening = createReservationsForScreening(screening, date)
            totalReservations += reservationsForScreening
            logger.debug("Created $reservationsForScreening reservations for ${screening.movie.name}")
        }

        logger.info("Day $date completed: ${screenings.size} screenings, $totalReservations reservations")
        return DaySeederResult(screenings.size, totalReservations)
    }

    private fun createPressSeatsBlocking(screening: ScreeningData, date: LocalDate) {
        logger.debug("Creating press seat blocks for premium movie '${screening.movie.name}' on $date")

        // Block 2-5 premium seats (front rows, center seats) for press
        val pressSeatsToBlock = random.nextInt(2, 6)
        val pressOwners = listOf("Press", "Media", "VIP", "Critics", "Industry")

        repeat(pressSeatsToBlock) {
            // Prefer front rows (0-2) and center columns (3-6) for press seats
            val row = random.nextInt(0, 3)
            val column = random.nextInt(3, 7)
            val seat = SeatNumber(row, column)
            val owner = pressOwners.random(random)

            val blockSeatCommand = BlockSeats(
                screeningId = screening.screeningId,
                seats = setOf(seat),
                blockadeOwner = owner,
                issuedAt = clock.instant()
            )

            when (val result = commandGateway.sendAndWait<CommandResult>(blockSeatCommand)) {
                is CommandResult.Success -> {
                    logger.debug("Successfully blocked seat (${seat.row},${seat.column}) for $owner for '${screening.movie.name}'")
                }
                is CommandResult.Failure -> {
                    logger.debug("Failed to block seat (${seat.row},${seat.column}) for $owner: ${result.message}")
                }
            }
        }

        logger.info("Press seat blocking completed for '${screening.movie.name}': attempted $pressSeatsToBlock blocks")
    }

    private fun createReservationsForScreening(screening: ScreeningData, date: LocalDate): Int {
        logger.debug("Starting reservation creation for '${screening.movie.name}' on $date at ${screening.startTime}")

        // Base probability for reservations based on movie popularity and day of week
        val dayOfWeekMultiplier = when (date.dayOfWeek.value) {
            6, 7 -> 1.5 // Weekend
            5 -> 1.2    // Friday
            else -> 1.0  // Weekdays
        }

        val timeMultiplier = when {
            screening.startTime.isAfter(LocalTime.of(18, 0)) -> 1.4 // Evening
            screening.startTime.isAfter(LocalTime.of(14, 0)) -> 1.1 // Afternoon
            else -> 0.7 // Morning
        }

        val reservationProbability = screening.movie.popularity * dayOfWeekMultiplier * timeMultiplier
        val maxReservations = (100 * reservationProbability).toInt().coerceAtMost(95) // Max 95% occupancy

        logger.debug("Reservation parameters for '${screening.movie.name}': popularity=${screening.movie.popularity}, dayMultiplier=$dayOfWeekMultiplier, timeMultiplier=$timeMultiplier, maxReservations=$maxReservations")

        // Generate 15-45 reservation attempts
        val reservationAttempts = random.nextInt(15, 46)
        logger.debug("Will attempt $reservationAttempts reservations for '${screening.movie.name}'")

        var successfulReservations = 0
        var failedReservations = 0
        var skippedReservations = 0
        val bookedSeats = mutableSetOf<SeatNumber>()

        repeat(reservationAttempts) { attempt ->
            if (bookedSeats.size >= maxReservations) {
                skippedReservations++
                return@repeat
            }

            // 20% chance for group bookings (2-4 seats), 80% individual
            val seatsToBook = if (random.nextDouble() < 0.2) random.nextInt(2, 5) else 1
            val availableSeats = generateAvailableSeats(bookedSeats, seatsToBook)

            if (availableSeats.isNotEmpty()) {
                val reservationId = ReservationId(UUID.randomUUID().toString())
                logger.trace("Attempt ${attempt + 1}: Starting reservation $reservationId for ${availableSeats.size} seats")

                // Start reservation
                val startReservationCommand = StartReservation(
                    reservationId = reservationId,
                    screeningId = screening.screeningId,
                    seats = availableSeats,
                    issuedAt = clock.instant()
                )

                when (val startResult = commandGateway.sendAndWait<CommandResult>(startReservationCommand)) {
                    is CommandResult.Success -> {
                        logger.trace("Reservation $reservationId started successfully")
                        // 85% conversion rate - confirm most reservations
                        if (random.nextDouble() < 0.85) {
                            val confirmSeatsCommand = ConfirmSeats(
                                reservationId = reservationId,
                                seats = availableSeats,
                                issuedAt = clock.instant()
                            )

                            when (val confirmResult = commandGateway.sendAndWait<CommandResult>(confirmSeatsCommand)) {
                                is CommandResult.Success -> {
                                    bookedSeats.addAll(availableSeats)
                                    successfulReservations++
                                    logger.trace("Reservation $reservationId confirmed for seats: ${availableSeats.map { "(${it.row},${it.column})" }}")
                                }
                                is CommandResult.Failure -> {
                                    failedReservations++
                                    logger.debug("Failed to confirm reservation $reservationId: ${confirmResult.message}")
                                }
                            }
                        } else {
                            logger.trace("Reservation $reservationId not confirmed (simulating user abandonment)")
                        }
                    }
                    is CommandResult.Failure -> {
                        failedReservations++
                        logger.debug("Failed to start reservation for ${availableSeats.size} seats: ${startResult.message}")
                    }
                }
            } else {
                skippedReservations++
                logger.trace("Attempt ${attempt + 1}: No available seats for $seatsToBook seats request")
            }
        }

        logger.info("Reservation creation completed for '${screening.movie.name}': $successfulReservations confirmed, $failedReservations failed, $skippedReservations skipped. Final occupancy: ${bookedSeats.size}/100 seats")
        return successfulReservations
    }

    private fun generateAvailableSeats(bookedSeats: Set<SeatNumber>, count: Int): Set<SeatNumber> {
        val availableSeats = (0..9).flatMap { row ->
            (0..9).map { column ->
                SeatNumber(row, column)
            }
        }.filter { it !in bookedSeats }

        logger.trace("Seat availability: ${availableSeats.size}/100 available, requesting $count seats")

        return if (availableSeats.size >= count) {
            val selectedSeats = availableSeats.shuffled(random).take(count).toSet()
            logger.trace("Selected seats: ${selectedSeats.map { "(${it.row},${it.column})" }}")
            selectedSeats
        } else {
            logger.trace("Not enough available seats: need $count, have ${availableSeats.size}")
            emptySet()
        }
    }
}

private data class ScreeningData(
    val screeningId: ScreeningId,
    val movie: Movie,
    val startTime: LocalTime,
    val endTime: LocalTime
)

private data class DaySeederResult(
    val screenings: Int,
    val reservations: Int
)

data class DataSeederResult(
    val summary: String,
    val details: List<String>,
    val errors: List<String>,
    val totalScreenings: Int,
    val totalReservations: Int
)