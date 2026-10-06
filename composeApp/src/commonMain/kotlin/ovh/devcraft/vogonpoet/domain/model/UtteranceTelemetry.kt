package ovh.devcraft.vogonpoet.domain.model

/**
 * One raw ghost-window transcription as broadcast by babelfish, plus the
 * local arrival stamp. Kept for the whole utterance — the telemetry
 * panel's Section 1 shows the full history of raw increments, including
 * overlapping revisions.
 */
data class GhostIncrement(
    /**
     * The raw ghost text exactly as sent in the payload (not re-composed).
     */
    val raw: String,
    /**
     * Server-measured ghost inference duration in ms, when provided.
     */
    val inferenceMs: Float? = null,
    /**
     * Local arrival offset in ms relative to the voice-detect arrival
     * (`UtteranceTelemetry.voiceDetectedArrivalMs`).
     */
    val arrivalOffsetMs: Long = 0,
)

/**
 * Client-side telemetry for the current (last) utterance, feeding the
 * three-section transcript telemetry panel:
 *
 * 1. `increments` — raw ghost window transcriptions (full history),
 * 2. `committed` / `live` — the composed ghost's stable part and
 *    revisable gray-italic tail,
 * 3. `finalText` + the server timing strip.
 *
 * Only the current occurrence is kept: a `transcription_reset` (which
 * babelfish emits right after the final, as post-utterance cleanup)
 * leaves everything untouched, and the next `listening` VAD edge fully
 * resets for a new utterance. A final therefore completes the panel —
 * ghost sections stay visible alongside it, never replaced by it.
 */
data class UtteranceTelemetry(
    /**
     * Bumped on every reset / new listening edge, so consumers can key
     * animations or empty states off the current occurrence.
     */
    val utteranceId: Int = 0,
    /**
     * Section 1: full history of raw ghost increments for this utterance.
     */
    val increments: List<GhostIncrement> = emptyList(),
    /**
     * Section 2, stable part of the composed ghost line.
     */
    val committed: String = "",
    /**
     * Section 2, revisable tail of the composed ghost line.
     */
    val live: String = "",
    /**
     * Section 3 final transcript; null while the utterance is still live.
     */
    val finalText: String? = null,
    /**
     * Server-side speech-onset → first-ghost delay in ms.
     */
    val firstGhostDelayMs: Float? = null,
    /**
     * Server-side duration of the blocking final transcription pass in ms.
     */
    val finalComputeMs: Float? = null,
    /**
     * Utterance audio duration in seconds, derived from the latest
     * ghost window end (`window[1] / 16000`).
     */
    val utteranceDurationMs: Float? = null,
    /**
     * Local arrival timestamp of the `listening` VAD edge; the basis for
     * the increments' `arrivalOffsetMs`. Null before the first edge.
     */
    val voiceDetectedArrivalMs: Long? = null,
)
