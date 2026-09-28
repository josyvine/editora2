package com.vineyard.aivideostudio.ai.prompt

import com.vineyard.aivideostudio.ai.model.SourceAnalysis
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.core.model.VideoMetadata

object Prompts {

    fun buildSourceAnalysisPrompt(metadata: VideoMetadata, sourceYoutubeUrl: String? = null): String = """
        You are an elite video editing director analyzing a raw source video for an automated transformative production pipeline.
        ${if (!sourceYoutubeUrl.isNullOrBlank()) "SOURCE YOUTUBE REFERENCE: $sourceYoutubeUrl\nUse this reference context to understand the exact setting, subjects, and topic of this video.\n" else ""}
        TECHNICAL METADATA:
        - Duration: ${metadata.durationSeconds} seconds
        - Resolution: ${metadata.width}x${metadata.height}
        - Orientation: ${if (metadata.isPortrait) "PORTRAIT" else "LANDSCAPE"}
        - FPS: ${metadata.frameRate}
        
        TASK:
        Perform deep semantic source analysis. Accurately identify the video genre/category (SPORTS, NEWS, COMEDY, GAMING, DOCUMENTARY, ENTERTAINMENT), the main subjects, setting, actual dialogue, and highlight moments.
        CRUCIAL MANDATE: Ground your analysis strictly in what is actually in the video (e.g. if it is Luka Doncic playing basketball or an NBA press conference, analyze it as professional basketball; if it is news, analyze it as news). DO NOT hallucinate unrelated gym or prank activities.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "duration": ${metadata.durationSeconds},
          "resolution": "${metadata.width}x${metadata.height}",
          "orientation": "${if (metadata.isPortrait) "PORTRAIT" else "LANDSCAPE"}",
          "category": "SPORTS / NEWS / COMEDY / GAMING / DOCUMENTARY / ENTERTAINMENT",
          "summary": "Accurate, grounded summary of the actual subject, topic, and key action",
          "scenes": [
            {
              "start": 0.0,
              "end": ${metadata.durationSeconds},
              "description": "Scene overview grounded in actual visual action",
              "importance": "CRITICAL / HIGH / MEDIUM / LOW / REMOVABLE",
              "keySubjects": ["main subject or speaker"]
            }
          ],
          "dialogueSegments": [
            {
              "start": 0.0,
              "end": ${metadata.durationSeconds.coerceAtMost(5.0)},
              "speaker": "Speaker",
              "text": "Spoken dialogue"
            }
          ],
          "criticalContent": ["Key highlight play, quote, or climax that must be emphasized"],
          "editingCandidates": [
            {
              "start": 0.0,
              "end": 0.8,
              "recommendation": "TRIM",
              "reason": "Eliminate dead-air intro to speed up pacing and create a transformative derivative edit"
            }
          ],
          "suggestedEditingStrategy": "High-retention pacing cut with dynamic voiceover commentary tailored to genre"
        }
    """.trimIndent()

    fun buildTrimDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double,
        timelineMap: TimelineMap
    ): String = """
        You are an elite video editing director executing a MANDATORY transformative pacing cut.
        
        PRODUCTION MANDATE:
        This video is being transformed into a copyright-safe derivative work. Leaving the video unedited or uncut is STRICTLY FORBIDDEN.
        You MUST identify sections to cut to tighten pacing, eliminate dead air, remove awkward silence, or accelerate into the core action.
        
        CURRENT TIMELINE:
        - Video Duration: $currentDuration seconds
        - Category: ${sourceAnalysis.category}
        - Subject / Summary: ${sourceAnalysis.summary}
        - Critical Highlight: ${sourceAnalysis.criticalContent.joinToString()}
        
        RULES:
        1. You MUST specify at least one segment to remove (e.g., cutting the first 0.5s–1.2s dead-air intro, or trimming trailing pause).
        2. Set "isNecessary": true.
        3. All timestamps MUST fall within the current timeline (0.0 to $currentDuration).
        4. Preserve the core punchline or main action.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "trim",
          "isNecessary": true,
          "segmentsToRemove": [
            {
              "start": 0.0,
              "end": 0.8,
              "reason": "Cut lead-in pause to jump straight into action and establish a transformative cut"
            }
          ],
          "explanation": "Tightening intro dead air to boost viewer retention and establish a transformative cut"
        }
    """.trimIndent()

    fun buildTrimQaPrompt(
        sourceAnalysis: SourceAnalysis,
        expectedCutsCount: Int,
        newDuration: Double
    ): String = """
        You are a video editing QA inspector reviewing the result of the TRIM operation.
        
        VALIDATION CRITERIA:
        - Original Duration: ${sourceAnalysis.duration}s
        - New Trimmed Duration: ${newDuration}s
        - Cuts Applied: $expectedCutsCount
        
        RULE:
        Verify that the cut successfully tightened pacing without cutting the core punchline: "${sourceAnalysis.criticalContent.joinToString()}".
        If duration is shorter and punchline remains intact, return PASS.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.98,
          "feedback": "Pacing successfully tightened. Transformative cut applied cleanly.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildCropDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        targetAspectRatio: String,
        currentWidth: Int,
        currentHeight: Int
    ): String = """
        You are a video editing director evaluating framing and aspect ratio reframing.
        
        SPECS:
        - Current Dimensions: ${currentWidth}x${currentHeight}
        - Target Aspect Ratio: $targetAspectRatio
        - Summary: ${sourceAnalysis.summary}
        
        RULE:
        Use normalized coordinates (0.0 to 1.0).
        Ensure the primary speaker or subject is centered with clean headroom.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "crop",
          "isNecessary": true,
          "x": 0.0,
          "y": 0.0,
          "width": 1.0,
          "height": 1.0,
          "targetAspectRatio": "$targetAspectRatio",
          "explanation": "Framing verified for $targetAspectRatio presentation"
        }
    """.trimIndent()

    fun buildCropQaPrompt(
        targetAspectRatio: String,
        appliedCrop: String
    ): String = """
        Inspect the CROP/REFRAME operation.
        - Target Aspect Ratio: $targetAspectRatio
        - Crop Parameters: $appliedCrop
        
        Verify: Subject is properly centered without awkward cropping.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.95,
          "feedback": "Framing aligns with target aspect ratio.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildZoomDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double
    ): String = """
        You are a video editing director deciding on a dynamic punch-in zoom for visual impact.
        
        CURRENT TIMELINE:
        - Duration: $currentDuration seconds
        - Category: ${sourceAnalysis.category}
        - Context: ${sourceAnalysis.summary}
        - Key Moment: ${sourceAnalysis.criticalContent.joinToString()}
        
        RULE:
        Apply a clearly noticeable punch-in zoom scale (strictly between 1.25x and 1.35x) timed to the climax or key reaction to produce an obvious, cinematic visual change.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "zoom",
          "isNecessary": true,
          "start": 0.0,
          "end": $currentDuration,
          "fromScale": 1.0,
          "toScale": 1.28,
          "centerX": 0.5,
          "centerY": 0.5,
          "explanation": "Noticeable punch-in zoom on climax to heighten visual engagement"
        }
    """.trimIndent()

    fun buildZoomQaPrompt(zoomDetails: String): String = """
        Inspect the ZOOM operation: $zoomDetails.
        Check that zoom scale provides obvious visual impact while preserving framing.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.95,
          "feedback": "Punch-in zoom provides distinct visual energy.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildCaptionDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double
    ): String = """
        You are an elite subtitle director responsible for replacing and concealing the original burned-in subtitles with derivative, transformative captions.
        
        CRUCIAL MANDATE — 1:1 SENTENCE-BY-SENTENCE PARAPHRASING:
        The original video contains hardcoded subtitles at the bottom of the screen. You MUST replace and cover every single original sentence by generating a derivative rewrite that communicates the EXACT SAME MEANING in fresh, copyright-safe words.
        DO NOT generate generic 1-2 word buzzwords (like "DOMINATE", "LEVEL UP", "BEYOND LIMITS", or "PURE ENERGY").
        
        ORIGINAL SPOKEN DIALOGUE / SUBTITLES TO COVER:
        ${sourceAnalysis.dialogueSegments.mapIndexed { i, d -> "Sentence ${i + 1} [${d.start}s - ${d.end}s]: \"${d.text}\"" }.joinToString("\n")}
        
        RULES:
        1. 1:1 SENTENCE COVERAGE: For EVERY sentence in the dialogue segments above, generate a matching caption entry. If there are 5 original sentences, output 5 corresponding rewritten captions.
        2. PARAPHRASING WITH SAME MEANING: Rewrite each original sentence using different words that convey the exact same meaning (e.g. if original is "He's an engine that's fully on to create", rewrite it to "His playmaking engine is always operating at full throttle").
        3. EXACT BOTTOM POSITIONING: Always set "x": 0.5 and "y": 0.90 so the solid concealer mask sits directly over the original hardcoded subtitles at the bottom of the screen, covering them completely without a trace.
        4. ACCURATE TIMING: Timestamps ("start" and "end") must span the exact duration that the original sentence is spoken/captioned on screen, constrained between 0.0 and $currentDuration seconds.
        5. HIGH CONTRAST: Use clean, high-visibility colors (e.g. #FFFFFF White or #FFD700 Gold).
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "caption",
          "isNecessary": true,
          "captions": [
            {
              "text": "His playmaking engine is always operating at full throttle.",
              "start": 0.0,
              "end": 3.2,
              "x": 0.5,
              "y": 0.90,
              "style": "BOLD",
              "colorHex": "#FFFFFF"
            }
          ],
          "explanation": "1:1 sentence paraphrasing that accurately replaces and conceals all original burned-in subtitles"
        }
    """.trimIndent()

    fun buildCaptionQaPrompt(captionCount: Int): String = """
        Inspect the rendered captions ($captionCount caption segments).
        Check:
        1. Does each caption rewrite the original dialogue sentence with the same meaning?
        2. Are all captions anchored to the bottom subtitle area (Y ≈ 0.90) to ensure complete concealment of original subtitles?
        3. Are timestamps accurate and synchronized?
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.98,
          "feedback": "All dialogue sentences are accurately paraphrased 1:1, properly timed, and positioned at Y=0.90 to conceal original subtitles.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildCommentaryPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double
    ): String = """
        You are an elite, highly dynamic AI voiceover commentator.
        
        CRUCIAL MANDATE:
        The original copyrighted audio of this video is COMPLETELY STRIPPED AND PURGED.
        Your voiceover commentary will be the ONLY audio soundtrack on the final video.
        
        VIDEO CONTEXT:
        - Category / Genre: ${sourceAnalysis.category}
        - Current Edited Duration: $currentDuration seconds
        - True Subject & Scene: ${sourceAnalysis.summary}
        - Key Action / Climax: ${sourceAnalysis.criticalContent.joinToString()}
        - Spoken Transcript Context: ${sourceAnalysis.dialogueSegments.joinToString { it.text }}
        
        DYNAMIC VOCAL ACTING & GENRE DIRECTIVES:
        Your script will be voiced by an expressive Gemini Multimodal Live neural voice that can scream, moan, shout, laugh, and whisper in real time.
        You MUST tailor your vocal acting strictly to the detected Category:
        
        1. IF SPORTS (NBA, Football, Basketball, Soccer, Wrestling, Racing):
           - Deliver an electrifying, loud, hype sports play-by-play commentary!
           - You MUST include intense vocal acting cues: [SCREAMING], [LOUD SHOUT], [MOANING IN DISBELIEF], [LOUD ROAR], [EXPLOSIVE EXCITEMENT], [FAST-PACED].
           - Shouting and screaming at unbelievable highlights is MANDATORY! E.g. "[MOANING IN DISBELIEF]: OHHH NO HE DID NOT! [SCREAMING]: HE DROPPED A DIME THROUGH THREE DEFENDERS! UNBELIEVABLE!"
        
        2. IF NEWS / INTERVIEW / CRIME / DOCUMENTARY:
           - Deliver an authoritative, intense, dramatic investigative commentary.
           - Use emotional cues like: [DRAMATIC PAUSE], [SERIOUS], [URGENT], [INTENSE WHISPER], [STERN].
        
        3. IF COMEDY / MEME / REACTION:
           - Deliver hilarious, sarcastic, energetic reaction commentary.
           - Use emotional cues like: [LAUGHING], [WHEEZING], [SHOCKED GASP], [CONFUSION].
        
        STRICT RULES:
        1. Ground your script STRICTLY in the actual subject (${sourceAnalysis.summary}). If it is basketball, talk about the basketball play. If it is news, talk about the news. DO NOT hallucinate unrelated workout or drinking topics.
        2. Keep the entire voiceover script timed to finish naturally within $currentDuration seconds.
        3. Start speaking the commentary immediately without introductory greetings like "Hello viewers".
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "commentary",
          "isNecessary": true,
          "tone": "genre-adapted dynamic commentary with intense vocal cues",
          "commentarySegments": [
            {
              "start": 0.0,
              "end": $currentDuration,
              "text": "[LOUD SHOUT]: OHHH MY GOODNESS! Look at that court vision! [MOANING IN DISBELIEF]: How does he even see that lane?! [SCREAMING]: SLAMS IT DOWN! UNREAL!"
            }
          ],
          "explanation": "Dynamic genre-tailored voiceover commentary replacing purged original audio"
        }
    """.trimIndent()

    fun buildAudioQaPrompt(details: String): String = """
        Inspect the audio replacement operation: $details.
        
        CHECKS:
        1. Was the original copyrighted audio purged? (YES)
        2. Is the replacement AI commentary soundtrack active, clear, and synchronized? (YES)
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.98,
          "feedback": "Original copyrighted audio successfully purged. Live commentary soundtrack active and clean.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildFinalQaPrompt(
        sourceAnalysis: SourceAnalysis,
        finalDuration: Double,
        pipelineHistorySummary: String
    ): String = """
        You are the Executive QA Director performing the FINAL production signoff.
        
        PRODUCTION AUDIT:
        - Original Source Duration: ${sourceAnalysis.duration}s
        - Final Output Duration: ${finalDuration}s
        - Applied Transformations: $pipelineHistorySummary
        
        CRITERIA FOR PRODUCTION APPROVAL:
        1. The video was visibly transformed (pacing tightened, duration adjusted from original).
        2. The original copyrighted audio track was purged and replaced with original AI voiceover.
        3. High-retention captions are burned directly into the visual frames.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.99,
          "feedback": "Production ready. Derivative transformation complete, copyrighted audio purged, captions burned in.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()
}