package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.request.UpsertWaterRequest
import com.vidasync_bff.dto.response.SupabaseWaterEventRow
import com.vidasync_bff.dto.response.SupabaseWaterDailyRow
import com.vidasync_bff.dto.response.WaterEventResponse
import com.vidasync_bff.dto.response.WaterDailyResponse
import com.vidasync_bff.dto.response.WaterHistoryResponse
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class WaterService(
    private val supabaseClient: SupabaseClient
) {

    private val log = LoggerFactory.getLogger(WaterService::class.java)
    private val dailyTableName = "water_daily_intake"
    private val eventsTableName = "water_intake_events"
    private val waterDailyTypeRef = object : ParameterizedTypeReference<List<SupabaseWaterDailyRow>>() {}
    private val waterEventTypeRef = object : ParameterizedTypeReference<List<SupabaseWaterEventRow>>() {}

    fun getDay(userId: String, date: String?): WaterDailyResponse? {
        val resolvedDate = resolveDate(date)
        log.info("Buscando panorama de agua: userId={}, date={}", userId, resolvedDate)

        return buildDayResponse(userId, resolvedDate)
    }

    fun getHistory(userId: String, startDate: String?, endDate: String?): WaterHistoryResponse {
        val resolvedEndDate = resolveDate(endDate)
        val resolvedStartDate = startDate?.takeIf { it.isNotBlank() }?.let(::resolveDate)
            ?: findFirstRelevantDate(userId, resolvedEndDate)
            ?: resolvedEndDate

        val start = LocalDate.parse(resolvedStartDate)
        val end = LocalDate.parse(resolvedEndDate)
        if (start.isAfter(end)) {
            throw IllegalArgumentException("startDate nao pode ser maior que endDate")
        }

        log.info(
            "Buscando historico de agua: userId={}, startDate={}, endDate={}",
            userId, resolvedStartDate, resolvedEndDate
        )

        val dailyRows = getDailyRows(userId, resolvedStartDate, resolvedEndDate)
        val rowsByDate = dailyRows.associateBy { it.date }
        val eventsByDate = getEventRowsByRange(userId, resolvedStartDate, resolvedEndDate)
            .groupBy { it.date }

        var carriedGoal = getLatestGoalOnOrBefore(userId, resolvedStartDate)
        val days = mutableListOf<WaterDailyResponse>()
        var cursor = start

        while (!cursor.isAfter(end)) {
            val currentDate = cursor.toString()
            val row = rowsByDate[currentDate]
            val eventRows = eventsByDate[currentDate].orEmpty()
            val effectiveGoal = row?.goalMl ?: carriedGoal
            val response = composeDayResponse(
                date = currentDate,
                row = row,
                eventRows = eventRows,
                effectiveGoal = effectiveGoal
            )

            if (row != null || eventRows.isNotEmpty() || effectiveGoal != null) {
                days.add(response)
            }

            if (row?.goalMl != null) {
                carriedGoal = row.goalMl
            }

            cursor = cursor.plusDays(1)
        }

        return WaterHistoryResponse(
            startDate = resolvedStartDate,
            endDate = resolvedEndDate,
            days = days
        )
    }

    fun upsert(userId: String, request: UpsertWaterRequest): WaterDailyResponse {
        val resolvedDate = resolveDate(request.date)
        validateRequest(request)

        log.info(
            "Atualizando agua do dia: userId={}, date={}, goalMl={}, deltaMl={}",
            userId, resolvedDate, request.goalMl, request.deltaMl
        )

        val existingRow = getDailyRow(userId, resolvedDate)
        val existingEvents = getEventRows(userId, resolvedDate)
        val currentConsumed = calculateConsumed(existingRow, existingEvents)

        val requestedDelta = request.deltaMl ?: 0
        val appliedDelta = when {
            requestedDelta > 0 -> requestedDelta
            requestedDelta < 0 -> maxOf(requestedDelta, -currentConsumed)
            else -> 0
        }
        val updatedConsumed = (currentConsumed + appliedDelta).coerceAtLeast(0)

        val carriedGoal = existingRow?.goalMl ?: getLatestGoalOnOrBefore(userId, resolvedDate)
        val goalToPersist = when {
            request.goalMl != null -> request.goalMl
            existingRow != null -> existingRow.goalMl
            else -> carriedGoal
        }
        val shouldPersistRow = existingRow != null || request.goalMl != null || appliedDelta != 0 || goalToPersist != null

        if (shouldPersistRow) {
            saveDailyRow(
                userId = userId,
                date = resolvedDate,
                existingRow = existingRow,
                goalMl = goalToPersist,
                consumedMl = updatedConsumed
            )
        }

        if (appliedDelta != 0) {
            saveEvent(
                userId = userId,
                date = resolvedDate,
                deltaMl = appliedDelta,
                eventType = if (appliedDelta > 0) "ADD" else "REMOVE"
            )
        }

        return buildDayResponse(userId, resolvedDate)
            ?: throw RuntimeException("Nao foi possivel montar o panorama de agua apos salvar")
    }

    private fun validateRequest(request: UpsertWaterRequest) {
        if (request.goalMl == null && request.deltaMl == null) {
            throw IllegalArgumentException("Informe goalMl, deltaMl ou ambos no POST /water")
        }
        if (request.goalMl != null && request.goalMl < 0) {
            throw IllegalArgumentException("goalMl nao pode ser negativo")
        }
        if (request.deltaMl != null && request.deltaMl == 0 && request.goalMl == null) {
            throw IllegalArgumentException("deltaMl nao pode ser zero sem uma nova meta")
        }
    }

    private fun buildDayResponse(userId: String, date: String): WaterDailyResponse? {
        val row = getDailyRow(userId, date)
        val events = getEventRows(userId, date)
        val effectiveGoal = row?.goalMl ?: getLatestGoalOnOrBefore(userId, date)

        if (row == null && events.isEmpty() && effectiveGoal == null) {
            return null
        }

        return composeDayResponse(
            date = date,
            row = row,
            eventRows = events,
            effectiveGoal = effectiveGoal
        )
    }

    private fun composeDayResponse(
        date: String,
        row: SupabaseWaterDailyRow?,
        eventRows: List<SupabaseWaterEventRow>,
        effectiveGoal: Int?
    ): WaterDailyResponse {
        val sortedEvents = eventRows.sortedWith(compareBy({ it.createdAt }, { it.id }))
        val persistedConsumed = row?.consumedMl ?: 0
        val rawEventTotal = sortedEvents.sumOf { it.deltaMl }
        val eventResponses = mutableListOf<WaterEventResponse>()
        var runningConsumed = 0

        if (persistedConsumed > rawEventTotal) {
            val adjustmentDelta = persistedConsumed - rawEventTotal
            runningConsumed += adjustmentDelta
            eventResponses.add(
                WaterEventResponse(
                    id = row?.id?.let { "legacy-$it" } ?: "legacy-$date",
                    date = date,
                    deltaMl = adjustmentDelta,
                    action = "ADJUSTMENT",
                    runningConsumedMl = runningConsumed
                )
            )
        }

        sortedEvents.forEach { event ->
            runningConsumed = (runningConsumed + event.deltaMl).coerceAtLeast(0)
            eventResponses.add(
                WaterEventResponse(
                    id = event.id,
                    date = event.date,
                    deltaMl = event.deltaMl,
                    action = resolveAction(event),
                    runningConsumedMl = runningConsumed
                )
            )
        }

        val consumedMl = maxOf(persistedConsumed, runningConsumed)
        val inheritedGoal = effectiveGoal != null && row?.goalMl == null

        return WaterDailyResponse.from(
            date = date,
            row = row,
            goalMl = effectiveGoal,
            consumedMl = consumedMl,
            goalInherited = inheritedGoal,
            events = eventResponses
        )
    }

    private fun resolveAction(event: SupabaseWaterEventRow): String {
        return when {
            !event.eventType.isNullOrBlank() -> event.eventType.uppercase()
            event.deltaMl > 0 -> "ADD"
            event.deltaMl < 0 -> "REMOVE"
            else -> "ADJUSTMENT"
        }
    }

    private fun calculateConsumed(
        row: SupabaseWaterDailyRow?,
        eventRows: List<SupabaseWaterEventRow>
    ): Int {
        val persistedConsumed = row?.consumedMl ?: 0
        val eventConsumed = eventRows.sumOf { it.deltaMl }
        return maxOf(persistedConsumed, eventConsumed, 0)
    }

    private fun saveDailyRow(
        userId: String,
        date: String,
        existingRow: SupabaseWaterDailyRow?,
        goalMl: Int?,
        consumedMl: Int
    ): SupabaseWaterDailyRow {
        val rows = if (existingRow == null) {
            val body = mutableMapOf<String, Any>(
                "user_id" to userId,
                "date" to date,
                "consumed_ml" to consumedMl
            )
            goalMl?.let { body["goal_ml"] = it }
            supabaseClient.post(dailyTableName, body, waterDailyTypeRef)
        } else {
            val body = mutableMapOf<String, Any>(
                "consumed_ml" to consumedMl
            )
            goalMl?.let { body["goal_ml"] = it }
            supabaseClient.patch(
                dailyTableName,
                mapOf("user_id" to "eq.$userId", "date" to "eq.$date"),
                body,
                waterDailyTypeRef
            )
        } ?: throw RuntimeException("Nao foi possivel salvar a ingestao de agua")

        return rows.firstOrNull() ?: throw RuntimeException("Resposta vazia ao salvar ingestao de agua")
    }

    private fun saveEvent(
        userId: String,
        date: String,
        deltaMl: Int,
        eventType: String
    ) {
        val rows = supabaseClient.post(
            eventsTableName,
            mapOf(
                "user_id" to userId,
                "date" to date,
                "delta_ml" to deltaMl,
                "event_type" to eventType
            ),
            waterEventTypeRef
        ) ?: throw RuntimeException("Nao foi possivel salvar o historico de agua")

        if (rows.isEmpty()) {
            throw RuntimeException("Resposta vazia ao salvar o historico de agua")
        }
    }

    private fun getDailyRow(userId: String, date: String): SupabaseWaterDailyRow? {
        val rows = supabaseClient.get(
            dailyTableName,
            mapOf(
                "user_id" to "eq.$userId",
                "date" to "eq.$date",
                "limit" to "1"
            ),
            waterDailyTypeRef
        ) ?: emptyList()

        return rows.firstOrNull()
    }

    private fun getDailyRows(userId: String, startDate: String, endDate: String): List<SupabaseWaterDailyRow> {
        return supabaseClient.get(
            dailyTableName,
            mapOf(
                "user_id" to "eq.$userId",
                "and" to "(date.gte.$startDate,date.lte.$endDate)",
                "order" to "date.asc"
            ),
            waterDailyTypeRef
        ) ?: emptyList()
    }

    private fun getEventRows(userId: String, date: String): List<SupabaseWaterEventRow> {
        return supabaseClient.get(
            eventsTableName,
            mapOf(
                "user_id" to "eq.$userId",
                "date" to "eq.$date",
                "order" to "created_at.asc,id.asc"
            ),
            waterEventTypeRef
        ) ?: emptyList()
    }

    private fun getEventRowsByRange(userId: String, startDate: String, endDate: String): List<SupabaseWaterEventRow> {
        return supabaseClient.get(
            eventsTableName,
            mapOf(
                "user_id" to "eq.$userId",
                "and" to "(date.gte.$startDate,date.lte.$endDate)",
                "order" to "date.asc,created_at.asc,id.asc"
            ),
            waterEventTypeRef
        ) ?: emptyList()
    }

    private fun getLatestGoalOnOrBefore(userId: String, date: String): Int? {
        val rows = supabaseClient.get(
            dailyTableName,
            mapOf(
                "user_id" to "eq.$userId",
                "date" to "lte.$date",
                "goal_ml" to "not.is.null",
                "order" to "date.desc",
                "limit" to "1"
            ),
            waterDailyTypeRef
        ) ?: emptyList()

        return rows.firstOrNull()?.goalMl
    }

    private fun findFirstRelevantDate(userId: String, endDate: String): String? {
        val firstDailyDate = supabaseClient.get(
            dailyTableName,
            mapOf(
                "user_id" to "eq.$userId",
                "date" to "lte.$endDate",
                "order" to "date.asc",
                "limit" to "1"
            ),
            waterDailyTypeRef
        )?.firstOrNull()?.date

        val firstEventDate = supabaseClient.get(
            eventsTableName,
            mapOf(
                "user_id" to "eq.$userId",
                "date" to "lte.$endDate",
                "order" to "date.asc",
                "limit" to "1"
            ),
            waterEventTypeRef
        )?.firstOrNull()?.date

        return listOfNotNull(firstDailyDate, firstEventDate).minOrNull()
    }

    private fun resolveDate(date: String?): String {
        if (date.isNullOrBlank()) return LocalDate.now().toString()

        return try {
            LocalDate.parse(date).toString()
        } catch (_: Exception) {
            throw IllegalArgumentException("date invalida. Use o formato YYYY-MM-DD")
        }
    }
}
