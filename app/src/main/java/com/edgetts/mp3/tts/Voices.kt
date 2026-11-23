package com.edgetts.mp3.tts

enum class Gender(val label: String) { Female("Female"), Male("Male") }

data class Voice(
    /** Edge short name, e.g. "en-US-AriaNeural". */
    val id: String,
    val name: String,
    val gender: Gender,
    val country: String,
    val tone: String,
    val bestFor: String,
) {
    val locale: String get() = id.substring(0, 5)
    val countryCode: String get() = id.substring(3, 5)

    /** Long form the websocket SSML expects: "Microsoft Server Speech Text to Speech Voice (en-US, AriaNeural)". */
    val ssmlName: String get() = "Microsoft Server Speech Text to Speech Voice ($locale, ${id.substring(6)})"
}

/**
 * English voices served by Edge Read Aloud (checked against the live voice list, Nov 2026).
 * Tone notes for en-US voices follow Microsoft's own personality tags; the rest are our listening notes.
 */
object Voices {
    val all: List<Voice> = listOf(
        // United States
        Voice("en-US-AvaNeural", "Ava", Gender.Female, "United States", "Expressive, caring and warm", "Audiobooks, personal essays and anything that benefits from emotional range"),
        Voice("en-US-AvaMultilingualNeural", "Ava Multilingual", Gender.Female, "United States", "Expressive and caring, handles foreign words cleanly", "Text that mixes English with names or phrases from other languages"),
        Voice("en-US-AndrewNeural", "Andrew", Gender.Male, "United States", "Warm, confident and honest", "Podcasts, explainers and long-form non-fiction"),
        Voice("en-US-AndrewMultilingualNeural", "Andrew Multilingual", Gender.Male, "United States", "Warm and confident, handles foreign words cleanly", "Multilingual documents, travel and research notes"),
        Voice("en-US-EmmaNeural", "Emma", Gender.Female, "United States", "Cheerful, clear and conversational", "Tutorials, blog posts and upbeat learning content"),
        Voice("en-US-EmmaMultilingualNeural", "Emma Multilingual", Gender.Female, "United States", "Cheerful and clear, handles foreign words cleanly", "Language-learning material and mixed-language articles"),
        Voice("en-US-BrianNeural", "Brian", Gender.Male, "United States", "Approachable, casual and sincere", "Newsletters, casual articles and friendly how-tos"),
        Voice("en-US-BrianMultilingualNeural", "Brian Multilingual", Gender.Male, "United States", "Casual and sincere, handles foreign words cleanly", "Casual content with non-English names or terms"),
        Voice("en-US-AriaNeural", "Aria", Gender.Female, "United States", "Positive, confident and polished", "News, reports and fiction narration"),
        Voice("en-US-JennyNeural", "Jenny", Gender.Female, "United States", "Friendly, considerate and comforting", "Everyday reading, assistants and wellbeing content"),
        Voice("en-US-MichelleNeural", "Michelle", Gender.Female, "United States", "Friendly and pleasant, even pacing", "Articles, novels and study notes"),
        Voice("en-US-AnaNeural", "Ana", Gender.Female, "United States", "Child voice, cute and bright", "Children's stories and playful content"),
        Voice("en-US-ChristopherNeural", "Christopher", Gender.Male, "United States", "Reliable and authoritative", "News, business reports and formal documents"),
        Voice("en-US-EricNeural", "Eric", Gender.Male, "United States", "Rational and measured", "Technical documentation, research papers and manuals"),
        Voice("en-US-GuyNeural", "Guy", Gender.Male, "United States", "Passionate and energetic", "Sports, storytelling and persuasive writing"),
        Voice("en-US-RogerNeural", "Roger", Gender.Male, "United States", "Lively and animated", "Fiction with dialogue and light entertainment"),
        Voice("en-US-SteffanNeural", "Steffan", Gender.Male, "United States", "Rational and steady", "Non-fiction, analysis and long study sessions"),

        // United Kingdom
        Voice("en-GB-SoniaNeural", "Sonia", Gender.Female, "United Kingdom", "Crisp, composed British RP", "Formal documents, literature and reports"),
        Voice("en-GB-LibbyNeural", "Libby", Gender.Female, "United Kingdom", "Warm, youthful British", "Blogs, lifestyle content and light fiction"),
        Voice("en-GB-MaisieNeural", "Maisie", Gender.Female, "United Kingdom", "Child voice, gentle British", "Children's stories and bedtime reading"),
        Voice("en-GB-RyanNeural", "Ryan", Gender.Male, "United Kingdom", "Friendly, natural British", "Audiobooks, podcasts and general narration"),
        Voice("en-GB-ThomasNeural", "Thomas", Gender.Male, "United Kingdom", "Calm, measured British", "Documentaries, history and educational material"),

        // Australia and New Zealand
        Voice("en-AU-NatashaNeural", "Natasha", Gender.Female, "Australia", "Bright, friendly Australian", "Everyday articles, guides and newsletters for Australian listeners"),
        Voice("en-AU-WilliamMultilingualNeural", "William Multilingual", Gender.Male, "Australia", "Relaxed, friendly Australian", "Casual narration and mixed-language text with an Australian accent"),
        Voice("en-NZ-MollyNeural", "Molly", Gender.Female, "New Zealand", "Warm, friendly Kiwi", "General reading and local content for NZ audiences"),
        Voice("en-NZ-MitchellNeural", "Mitchell", Gender.Male, "New Zealand", "Easygoing, friendly Kiwi", "Casual narration and informal guides"),

        // Canada and Ireland
        Voice("en-CA-ClaraNeural", "Clara", Gender.Female, "Canada", "Clear, friendly Canadian", "Neutral North American narration and e-learning"),
        Voice("en-CA-LiamNeural", "Liam", Gender.Male, "Canada", "Friendly, even Canadian", "Explainers and general non-fiction"),
        Voice("en-IE-EmilyNeural", "Emily", Gender.Female, "Ireland", "Soft, friendly Irish", "Storytelling, poetry and gentle long-form reading"),
        Voice("en-IE-ConnorNeural", "Connor", Gender.Male, "Ireland", "Warm, friendly Irish", "Storytelling and conversational content"),

        // Asia
        Voice("en-IN-NeerjaNeural", "Neerja", Gender.Female, "India", "Friendly, clear Indian English", "General reading and content for Indian audiences"),
        Voice("en-IN-NeerjaExpressiveNeural", "Neerja Expressive", Gender.Female, "India", "Animated and expressive Indian English", "Stories and engaging explainers"),
        Voice("en-IN-PrabhatNeural", "Prabhat", Gender.Male, "India", "Friendly, steady Indian English", "Lectures, tutorials and business content"),
        Voice("en-SG-LunaNeural", "Luna", Gender.Female, "Singapore", "Friendly, crisp Singaporean English", "General reading and local guides"),
        Voice("en-SG-WayneNeural", "Wayne", Gender.Male, "Singapore", "Friendly, measured Singaporean English", "Explainers and informational content"),
        Voice("en-HK-YanNeural", "Yan", Gender.Female, "Hong Kong", "Friendly, light Hong Kong English", "General reading for Hong Kong audiences"),
        Voice("en-HK-SamNeural", "Sam", Gender.Male, "Hong Kong", "Friendly, even Hong Kong English", "Informational and business content"),
        Voice("en-PH-RosaNeural", "Rosa", Gender.Female, "Philippines", "Friendly, warm Filipino English", "General reading and customer-facing content"),
        Voice("en-PH-JamesNeural", "James", Gender.Male, "Philippines", "Friendly, upbeat Filipino English", "Casual narration and guides"),

        // Africa
        Voice("en-ZA-LeahNeural", "Leah", Gender.Female, "South Africa", "Friendly, poised South African", "General reading and professional content"),
        Voice("en-ZA-LukeNeural", "Luke", Gender.Male, "South Africa", "Friendly, confident South African", "Narration, reports and explainers"),
        Voice("en-NG-EzinneNeural", "Ezinne", Gender.Female, "Nigeria", "Friendly, bright Nigerian English", "General reading and local content"),
        Voice("en-NG-AbeoNeural", "Abeo", Gender.Male, "Nigeria", "Friendly, grounded Nigerian English", "Narration and informational content"),
        Voice("en-KE-AsiliaNeural", "Asilia", Gender.Female, "Kenya", "Friendly, gentle Kenyan English", "General reading and storytelling"),
        Voice("en-KE-ChilembaNeural", "Chilemba", Gender.Male, "Kenya", "Friendly, calm Kenyan English", "Explainers and informational content"),
        Voice("en-TZ-ImaniNeural", "Imani", Gender.Female, "Tanzania", "Friendly, soft Tanzanian English", "General reading and storytelling"),
        Voice("en-TZ-ElimuNeural", "Elimu", Gender.Male, "Tanzania", "Friendly, steady Tanzanian English", "Educational and informational content"),
    )

    val default: Voice = all.first { it.id == "en-US-AndrewNeural" }

    fun byId(id: String?): Voice = all.firstOrNull { it.id == id } ?: default
}
