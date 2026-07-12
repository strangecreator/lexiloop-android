package ru.lexiloop.app

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lexiloop.app.data.api.NextCardResponse
import ru.lexiloop.app.data.api.OverviewResponse
import ru.lexiloop.app.data.api.Paged
import ru.lexiloop.app.data.api.PoolDto

class DtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `next card payload with mixed example shapes decodes`() {
        val payload = """
            {
              "card": {
                "id": 42,
                "pool": 3,
                "pool_name": "Everyday verbs",
                "term": "meander",
                "part_of_speech": "verb",
                "ipa": "/miˈændər/",
                "short_definition": "to wander slowly",
                "definition": "To move slowly without a clear direction.",
                "examples": [
                  {"sentence": "The river meanders through the valley.", "note": ""},
                  "We meandered around the old town."
                ],
                "synonyms": ["wander", "roam"],
                "aliases": [],
                "suspended": false,
                "has_image": true,
                "image_key": "cards/42-abc.jpg",
                "schedule": {"state": "review", "due_at": "2026-07-12T10:00:00Z",
                             "interval_days": 6.5, "ease_factor": 2.4,
                             "repetitions": 4, "lapses": 1, "last_reviewed_at": null},
                "unknown_future_field": {"nested": true}
              },
              "direction": "term_to_definition",
              "prompt": "meander",
              "mode": "due",
              "queue_count": 12,
              "queue_breakdown": {"new": 2, "learning": 4, "review": 6},
              "show_images": true
            }
        """.trimIndent()

        val decoded = json.decodeFromString<NextCardResponse>(payload)
        val card = decoded.card
        assertNotNull(card)
        assertEquals(42, card!!.id)
        assertEquals("meander", card.term)
        assertTrue(card.hasImage)
        assertEquals("review", card.schedule?.state)

        val examples = card.exampleSentences()
        assertEquals(2, examples.size)
        assertEquals("The river meanders through the valley.", examples[0].sentence)
        assertEquals("We meandered around the old town.", examples[1].sentence)

        assertEquals("term_to_definition", decoded.direction)
        assertEquals(12, decoded.queueCount)
        assertEquals(4, decoded.queueBreakdown?.learning)
        assertFalse(decoded.practiceComplete)
    }

    @Test
    fun `empty queue payload decodes without a card`() {
        val payload = """
            {"card": null, "mode": "due", "message": "Nothing is due. You are caught up.",
             "queue_count": 0, "round_total": 0, "round_completed": 0,
             "queue_breakdown": {"new": 0, "learning": 0, "review": 0}}
        """.trimIndent()

        val decoded = json.decodeFromString<NextCardResponse>(payload)
        assertEquals(null, decoded.card)
        assertEquals("Nothing is due. You are caught up.", decoded.message)
    }

    @Test
    fun `paged pools decode`() {
        val payload = """
            {"count": 2, "next": null, "previous": null, "results": [
              {"id": 1, "name": "Idioms", "accent": "#F97316", "archived": false,
               "card_count": 120, "due_count": 7,
               "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-07-01T00:00:00Z"},
              {"id": 2, "name": "Archive", "archived": true, "card_count": 5, "due_count": 0}
            ]}
        """.trimIndent()

        val decoded = json.decodeFromString<Paged<PoolDto>>(payload)
        assertEquals(2, decoded.count)
        assertEquals("Idioms", decoded.results[0].name)
        assertEquals(7, decoded.results[0].dueCount)
        assertTrue(decoded.results[1].archived)
    }

    @Test
    fun `overview payload decodes`() {
        val payload = """
            {"total_cards": 1051, "due_now": 14, "new_cards": 30, "reviews_today": 22,
             "retention": 87.3, "streak": 9,
             "activity": [{"day": "2026-07-10", "reviews": 15}]}
        """.trimIndent()

        val decoded = json.decodeFromString<OverviewResponse>(payload)
        assertEquals(1051, decoded.totalCards)
        assertEquals(87.3, decoded.retention, 0.0001)
        assertEquals(1, decoded.activity.size)
    }
}
