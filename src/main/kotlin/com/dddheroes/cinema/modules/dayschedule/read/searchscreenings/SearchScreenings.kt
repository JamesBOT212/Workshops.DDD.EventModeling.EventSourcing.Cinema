package com.dddheroes.cinema.modules.dayschedule.read.searchscreenings

import com.dddheroes.cinema.modules.dayschedule.MovieId
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningCancelled
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningScheduled
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.axonframework.eventhandling.EventHandler
import org.axonframework.eventhandling.ResetHandler
import org.axonframework.queryhandling.QueryHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.domain.Specification
import jakarta.persistence.criteria.Predicate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalTime
import org.axonframework.extensions.kotlin.query
import org.axonframework.queryhandling.QueryGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

data class SearchScreenings(
    val screeningId: ScreeningId? = null,
    val movieId: MovieId? = null,
    val day: LocalDate? = null,
)
data class SearchScreeningsResult(val items: List<ScreeningReadModel>)

@Entity
@Table(name = "read_model_screening")
data class ScreeningReadModel(
    @Id
    val screeningId: String,
    val dayScheduleId: String,
    val day: LocalDate,
    val movieId: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

@ConditionalOnProperty(name = ["slices.dayschedule.read.searchscreenings.enabled"])
@Repository
private interface ScreeningReadModelRepository : JpaRepository<ScreeningReadModel, String>, JpaSpecificationExecutor<ScreeningReadModel>

@ConditionalOnProperty(name = ["slices.dayschedule.read.searchscreenings.enabled"])
@Component
private class ScreeningReadModelProjector(val repository: ScreeningReadModelRepository) {

    @EventHandler
    fun handle(event: ScreeningScheduled) =
        repository.save(
            ScreeningReadModel(
                screeningId = event.screeningId.toString(),
                dayScheduleId = event.dayScheduleId.toString(),
                day = event.dayScheduleId.toLocalDate(),
                movieId = event.movieId.raw,
                startTime = event.startTime,
                endTime = event.endTime
            )
        )

    @EventHandler
    fun handle(event: ScreeningCancelled) =
        repository.deleteById(event.screeningId.raw)

    @ResetHandler
    fun onReset() =
        repository.deleteAll()

}

@ConditionalOnProperty(name = ["slices.dayschedule.read.searchscreenings.enabled"])
@Component
private class SearchScreeningsQueryHandler(private val repository: ScreeningReadModelRepository) {

    @QueryHandler
    fun handle(query: SearchScreenings): SearchScreeningsResult {
        val spec = Specification<ScreeningReadModel> { root, _, criteriaBuilder ->
            val predicates = mutableListOf<Predicate>()

            query.screeningId?.let {
                predicates.add(criteriaBuilder.equal(root.get<String>("screeningId"), it.raw))
            }
            query.movieId?.let {
                predicates.add(criteriaBuilder.equal(root.get<String>("movieId"), it.raw))
            }
            query.day?.let {
                predicates.add(criteriaBuilder.equal(root.get<LocalDate>("day"), it))
            }

            if (predicates.isEmpty()) {
                criteriaBuilder.conjunction()
            } else {
                criteriaBuilder.and(*predicates.toTypedArray())
            }
        }

        return SearchScreeningsResult(repository.findAll(spec))
    }

}

@ConditionalOnProperty(name = ["slices.dayschedule.read.searchscreenings.enabled"])
@RestController
@RequestMapping("/cinema")
internal class SearchScreeningsRestApi(private val queryGateway: QueryGateway) {

    @GetMapping("/screenings")
    fun searchScreenings(
        @RequestParam(required = false) screeningId: String?,
        @RequestParam(required = false) movieId: String?,
        @RequestParam(required = false) day: LocalDate?
    ): CompletableFuture<SearchScreeningsResult> = queryGateway.query(
        SearchScreenings(
            screeningId = screeningId?.let { ScreeningId.of(it) },
            movieId = movieId?.let { MovieId(it) },
            day = day
        )
    )

}
