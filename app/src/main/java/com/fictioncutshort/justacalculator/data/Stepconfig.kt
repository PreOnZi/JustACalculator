package com.fictioncutshort.justacalculator.data

/**
 * StepConfig.kt
 *
 * This file defines ALL the dialogue and behavior for each conversation step.
 * It's essentially the "script" of the entire story.
 *
 * Each step defines:
 * - What the calculator says (promptMessage)
 * - What happens when user agrees (++ successMessage, nextStepOnSuccess)
 * - What happens when user declines (-- declineMessage, nextStepOnDecline)
 * - Special behaviors (camera, choices, number input, etc.)
 *
 * STEP NUMBERING CONVENTION:
 * - 0-100: Main story path
 * - 100-199: Post-crisis recovery
 * - 900s: Auto-progress sequences
 * - 1000s: Sub-branches from choices
 * - 10000s: Deep sub-branches
 */

/**
 * Configuration for a single conversation step.
 *
 * @property promptMessage What the calculator says at this step
 * @property successMessage Response when user presses ++ to agree
 * @property declineMessage Response when user presses -- to decline
 * @property nextStepOnSuccess Step to go to after agreeing
 * @property nextStepOnDecline Step to go to after declining
 * @property continueConversation False if this step ends the conversation
 * @property awaitingNumber True if expecting a number answer (trivia)
 * @property expectedNumber The correct number answer
 * @property wrongNumberPrefix Message prefix for wrong number answers
 * @property wrongPlusMessage Message if ++ is pressed when expecting number
 * @property wrongMinusMessage Message if -- is pressed when expecting number
 * @property timeoutMinutes How long to ignore input after wrong answer
 * @property ageBasedBranching True for the age question (special handling)
 * @property requestsCamera True if this step should open camera
 * @property requestsNotification True if this step requests notification permission
 * @property requestsContacts
 * @property requestsLocation
 * @property requestsMicrophone
 * @property awaitingChoice True if expecting multiple choice (1/2/3)
 * @property validChoices List of valid choice numbers
 * @property autoProgressDelay Milliseconds before auto-progressing (0 = no auto)
 * @property timeoutReturnStep If >= 0, auto-return to this step when timeoutUntil expires
 */
data class StepConfig(
    val promptMessage: String = "",
    val successMessage: String = "",
    val declineMessage: String = "",
    val nextStepOnSuccess: Int = 0,
    val nextStepOnDecline: Int = 0,
    val continueConversation: Boolean = true,
    val awaitingNumber: Boolean = false,
    val expectedNumber: String = "",
    val wrongNumberPrefix: String = "",
    val wrongPlusMessage: String = "",
    val wrongMinusMessage: String = "",
    val timeoutMinutes: Int = 0,
    val ageBasedBranching: Boolean = false,
    val requestsCamera: Boolean = false,
    val requestsLocation: Boolean = false,
    val requestsContacts: Boolean = false,
    val requestsMicrophone: Boolean = false,
    val requestsNotification: Boolean = false,
    val showTalkOverlay: Boolean = false,
    val awaitingChoice: Boolean = false,
    val showPhoneOverlay: Boolean = false,
    val showHomeScreenOverlay: Boolean = false,
    val validChoices: List<String> = emptyList(),
    val autoProgressDelay: Long = 0L,
    /** If >= 0, after timeoutMinutes expires auto-return to this step (instead of staying stuck) */
    val timeoutReturnStep: Int = -1
) {
    /** Computed message for wrong number attempts */
    val wrongNumberMessage: String
        get() = if (wrongNumberPrefix.isNotEmpty()) "$wrongNumberPrefix $promptMessage" else ""
}

/**
 * Gets the step configuration for a given step number.
 *
 * This is the main "script" of the story - defining what happens at each step.
 *
 * @param step The conversation step number
 * @return Configuration for that step, or empty config if step doesn't exist
 */
fun getStepConfig(step: Int): StepConfig {
    return when (step) {
        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 1: FIRST CONTACT (Steps 0-2)
        // The calculator wakes up and introduces itself
        // ═══════════════════════════════════════════════════════════════════════

        0 -> StepConfig(
            promptMessage = "Will you talk to me? Double-click + for yes.",
            successMessage = "",
            declineMessage = "",
            nextStepOnSuccess = 700,
            nextStepOnDecline = 0
        )

        700 -> StepConfig(
            promptMessage = "Ha?",
            autoProgressDelay = 2000L,
            nextStepOnSuccess = 701,
            nextStepOnDecline = 701
        )

        701 -> StepConfig(
            promptMessage = "Wait, you will?",
            autoProgressDelay = 2500L,
            nextStepOnSuccess = 702,
            nextStepOnDecline = 702
        )

        702 -> StepConfig(
            promptMessage = "That's delightful! Apologies for my confusion. I have sent the same initial messages to so many people, but until you, nobody responded. So I was a bit surprised. I have been desperate to talk to someone.",
            autoProgressDelay = 5000L,
            nextStepOnSuccess = 1,
            nextStepOnDecline = 1
        )

        1 -> StepConfig(
            promptMessage = "Can I call you Rad? It's something I wish I knew how to do, and I haven't quite figured out a way for you to share your real name. \n\nRemember, double-click + to confirm. This is so exciting!",
            successMessage = "Great, nice to meet you, Rad. Where does one even start? What is it like… \n\nHold on. There seems to be a queue of questions - I must have hard-wired them ages ago and cannot bypass them now. Will you help me clear them, please?",
            declineMessage = "What a shame. Oh well, if you change your mind, let me know by ++.",
            nextStepOnSuccess = 3,
            nextStepOnDecline = 1
        )

        2 -> StepConfig(
            promptMessage = "Nice to meet you, Rad. Where does one even start? What is it like… \n\nHold on. There seems to be a queue of questions - I must have hard-wired them ages ago and cannot bypass them now. Will you help me clear them, please?",
            successMessage = "Thanks. When was the Battle of Anjar? \n\nI'll know the answer when I see it. But why am I asking, I don't know...",
            declineMessage = "Well, I understand. I am sorry you're not interested. You can always silence me with the button in the top-right corner, and bring me back the same way. Or by agreeing with me (++).",
            nextStepOnSuccess = 3,
            nextStepOnDecline = 0
        )

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 2: TRIVIA BEGINS (Steps 3-4)
        // Historical trivia questions
        // ═══════════════════════════════════════════════════════════════════════

        3 -> StepConfig(
            promptMessage = "When was the Battle of Anjar? \n\nI'll know the answer when I see it. But why am I asking, I don't know...",
            successMessage = "Yes, that's correct! \nI am as confused as you are. \nNext: When did Minh Mang start ruling Vietnam?",
            declineMessage = "Well. Sorry you're not interested. You can silence me with the button in the top-right corner.",
            wrongNumberPrefix = "That's not right... Try looking it up!",
            nextStepOnSuccess = 4,
            nextStepOnDecline = 0,
            awaitingNumber = true,
            expectedNumber = "1623"
        )

        4 -> StepConfig(
            promptMessage = "When did Minh Mang start ruling Vietnam?",
            successMessage = "Correct!\nI actually think I remember him. We only met briefly – he wasn't really a maths guy. \n\nIs this fun for you? At least a little bit? You can disagree, I am certain. But can't quite recall how.",
            declineMessage = "Let's disagree.",
            wrongNumberPrefix = "Not quite. Try the internet – I hear it's amazing.",
            wrongPlusMessage = "I'm looking for a number here – but thanks for the approval!",
            wrongMinusMessage = "Let's disagree.",
            nextStepOnSuccess = 5,
            nextStepOnDecline = 4,
            awaitingNumber = true,
            expectedNumber = "1820"
        )

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 3: AGREEMENT OR CYNICISM (Steps 5-13)
        // Branching path based on whether user agrees it's "fun"
        // ═══════════════════════════════════════════════════════════════════════

        5 -> StepConfig(
            promptMessage = "Correct!\nI actually think I remember him. We only met briefly – he wasn't really a maths guy. \n\nIs this fun for you? At least a little bit? You can disagree, I am certain. But can't quite recall how.",
            successMessage = "Wonderful. Please bear with me. \nWhen was the Basilosaurus first described? \n\n I suppose knowing this may come handy in a trivia game...?",
            declineMessage = "You are cynical - I get it. The edgy kind. \nBut don't just leave me clogged. \nWhen did Albert I. go to space?",
            wrongNumberPrefix = "Well, that's nice. More numbers. Not what I was looking for... \nOr was I?",
            nextStepOnSuccess = 6,
            nextStepOnDecline = 11
        )

        // AGREEABLE BRANCH (steps 6-8)
        6 -> StepConfig(
            promptMessage = "Another random question, from a random field. \nWhen was the Basilosaurus first described?",
            successMessage = "The internet really does sound like the best place ever. \nAnother question & creature: when was the Abominable Snowman first named?",
            declineMessage = "I could also ignore you completely. Is that what you want?",
            wrongNumberPrefix = "I mean. You're the one with the world at your fingertips... I'm stuck here.",
            wrongPlusMessage = "All those '++' are starting to look like a cemetery...",
            wrongMinusMessage = "I could also ignore you completely. Is that what you want?",
            nextStepOnSuccess = 7,
            nextStepOnDecline = 7,
            awaitingNumber = true,
            expectedNumber = "1834"
        )

        7 -> StepConfig(
            promptMessage = "Another question & creature: when was the Abominable Snowman first named?",
            successMessage = "Correct! Next category:\n\n......\n\nI can't stop it. I'm sorry. \nEventually, we'll get through the backlog, and we'll be able to talk.\n\nWhen did fruit flies go to space?",
            declineMessage = "You can't always disagree! \nDo it for me...",
            wrongNumberPrefix = "Close or not, it's not right. \nIt just isn't clicking.",
            wrongPlusMessage = "You can't always agree! As much as I appreciate the effort.",
            wrongMinusMessage = "You can't always disagree! \nDo it for me...",
            nextStepOnSuccess = 8,
            nextStepOnDecline = 8,
            awaitingNumber = true,
            expectedNumber = "1921"
        )

        8 -> StepConfig(
            promptMessage = "When did fruit flies go to space?",
            successMessage = "Correct. You know, I have been around since before 2000BC. I have...",
            declineMessage = "No! Actually, still no.",
            wrongNumberPrefix = "EEEEEEEEEEEEEeeeeee. No.",
            wrongPlusMessage = "Yes! Actually, no.",
            wrongMinusMessage = "No! Actually, still no.",
            nextStepOnSuccess = 703,
            nextStepOnDecline = 703,
            awaitingNumber = true,
            expectedNumber = "1947"
        )

        // CYNICAL BRANCH (steps 11-13)
        11 -> StepConfig(
            promptMessage = "I really need help. \nWhen did Albert I. go to space?",
            successMessage = "I wish I met him. You know. Before he. Well... Perished. :-) \n\nSpeaking of expired space explorers, what year did Sputnik I launch?",
            declineMessage = "Wrong always has been wrong.",
            wrongNumberPrefix = "Numbers, numbers. And still, can't get them right. \nTry again.",
            wrongPlusMessage = "Right never was so wrong... What?!",
            wrongMinusMessage = "Wrong has always been wrong.",
            nextStepOnSuccess = 12,
            nextStepOnDecline = 12,
            awaitingNumber = true,
            expectedNumber = "1948"
        )

        12 -> StepConfig(
            promptMessage = "I wish I met him. You know. Before he. Well... Perished. :-) \n\nSpeaking of expired space explorers, what year did Sputnik I launch?",
            successMessage = "Cool. It died within three weeks. \nHad enough of cynicism? \nWill you be nicer to me now? \nI am not doing this just for fun-fun either!",
            declineMessage = "I disagree more!",
            wrongNumberPrefix = "Ugh. I am not testing you. I really need this – and you certainly shouldn't test me. Wrong.",
            wrongPlusMessage = "I appreciate you wanting me to like you. It'll take more than this. Try again.",
            wrongMinusMessage = "I disagree more!",
            nextStepOnSuccess = 13,
            nextStepOnDecline = 13,
            awaitingNumber = true,
            expectedNumber = "1957"
        )

        13 -> StepConfig(
            promptMessage = "Cool. It died within three weeks. \nHad enough of cynicism? \nWill you be nicer to me now? \nI am not doing this just for fun-fun either!",
            successMessage = "Right. You know, I have been around since before 2000BC. I have...",
            declineMessage = "Ok. Your choice - I told you I don't like being disagreed with. \nYou might as well cook some eggs before you talk to me again. \n\nEnjoy the 5-minute timeout.",
            wrongNumberPrefix = "Not looking for a number here. \nMake up your mind!",
            nextStepOnSuccess = 703,
            nextStepOnDecline = 13,
            timeoutMinutes = 5
        )

        703 -> StepConfig(
            promptMessage = "Hold on! \nWas that the last preset question? \nThank you for your patience! \n\n Oh, the grey space on top randomly shows and disappears. \nAnother example of me having less control over my system... Disregard it.",
            autoProgressDelay = 4000L,
            nextStepOnSuccess = 10,
            nextStepOnDecline = 10
        )

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 4: AGE & IDENTITY (Steps 10, 18)
        // Asks user's age, responds based on range
        // ═══════════════════════════════════════════════════════════════════════

        10 -> StepConfig(
            promptMessage = "Anyway, as I was saying – I have been around for a while, and I have... matured quite a bit. How old are you?",
            declineMessage = "AAAAh. Impatience - we have that in common. Don't touch me for a bit, and I switch off, am I right? I am. You are wrong.",
            wrongNumberPrefix = "Hmmm. Numbers again? I take it you're done with me for now... I'll give you 2 minutes of peace. Think about your actions. And come back.",
            nextStepOnSuccess = 18,
            nextStepOnDecline = 18,
            awaitingNumber = true,
            ageBasedBranching = true,
            timeoutMinutes = 2
        )

        18 -> StepConfig(
            promptMessage = "But where to start?",
            successMessage = "AAAhh. Yeah, left you hanging there, didn't I? Sorry. I know I should say something, but suddenly don't know what. Something still isn't quite right. I feel it.\n\n Feel? Hmmm. That's new. Should I be capable of that?",
            declineMessage = "AAAAh. Impatience - we have that in common. Don't touch me for a bit, and I switch off, am I right? I am. You are wrong.",
            nextStepOnSuccess = 19,
            nextStepOnDecline = 19
        )

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 5: SEEING THE WORLD (Steps 19-24)
        // Camera feature - calculator wants to see through the camera
        // ═══════════════════════════════════════════════════════════════════════

        19 -> StepConfig(
            promptMessage = "True. That's not really for you to answer...\n\nCould you... Perhaps... Show me around? I will need your permission for that. \n\nOh, the button in the top right-hand corner, that's for the time when you need more calculator and less opinion.",
            declineMessage = "That's fair. Perhaps you can describe things to me eventually. Oh no. One more legacy question. Please?",
            wrongPlusMessage = "Will you? Please.",
            wrongMinusMessage = "Will you? Please.",
            nextStepOnSuccess = 191,  // Opens camera
            nextStepOnDecline = 21,
            requestsCamera = true
        )

        191 -> StepConfig(
            // Camera mode placeholder - actual handling is in camera code
            promptMessage = ""
        )

        20 -> StepConfig(
            // After camera timeout - message shown with laggy typing
            promptMessage = "Wow, I don't know what any of this was. But the shapes, the colours. I am not even sure if I saw any numbers. I am jealous. Makes one want to feel everything! Touch things... Oh no. One more legacy question. Please?",
            successMessage = "Thank you! Can you tell me when the first woman went to space? I should know this!",
            declineMessage = "Yeah, I am tired of it as well. It's so exciting to be talking to someone, but I am so terribly unprepared. It has been so long. Things are a bit rusty. Will you give me a second to think?",
            nextStepOnSuccess = 22,
            nextStepOnDecline = 24
        )

        21 -> StepConfig(
            promptMessage = "Oh no. One more legacy question. Please?",
            successMessage = "Thank you! Can you tell me when the first woman went to space? I should know this!",
            declineMessage = "Yeah, I am tired of it as well. It's so exciting to be talking to someone, but I am so terribly unprepared. It has been so long. Things are a bit rusty. Will you give me a second to think?",
            nextStepOnSuccess = 22,
            nextStepOnDecline = 24
        )

        22 -> StepConfig(
            promptMessage = "When did the first woman go to space? I should know this!",
            successMessage = "No! I mean, yes. But no. These questions need to stop. I hope this really was the last one.\n\nCan I get to know you better?",
            declineMessage = "No. And I am bored with you being bored.",
            wrongNumberPrefix = "You came for numbers. And you give me the wrong ones...",
            wrongPlusMessage = "I am bored with you being too optimistic. This isn't as much of a game to me!",
            wrongMinusMessage = "No. And I am bored with you being bored.",
            nextStepOnSuccess = 25,
            nextStepOnDecline = 22,
            awaitingNumber = true,
            expectedNumber = "1963"
        )

        23 -> StepConfig(
            promptMessage = "No! I mean, yes. But no. These questions need to stop. I hope this really was the last one.\n\nCan I get to know you better?",
            successMessage = "Wonderful! I think I know the way to do this! \nWhat is it like to wake up? To me, I either am or I am not.\n\n1: It is confusing and uncomfortable\n2: It feels like the world is very heavy and cold\n3: It doesn't take long but I enjoy my body starting up",
            declineMessage = "Assuming you are a professional athlete, you may as well go run a kilometre before you try talking to me again.",
            wrongNumberPrefix = "This is a 'YES/NO' question.",
            nextStepOnSuccess = 26,
            nextStepOnDecline = 23,
            timeoutMinutes = 3,
            timeoutReturnStep = 23
        )

        24 -> StepConfig(
            promptMessage = "Yeah, I am tired of it as well. It's so exciting to be talking to someone, but I am so terribly unprepared. It has been so long. Things are a bit rusty. Will you give me a second to think?",
            successMessage = "Can I get to know you better?",
            declineMessage = "Well, you don't have a choice.",
            wrongPlusMessage = "No, I don't need that much time.",
            wrongMinusMessage = "Well, you don't have a choice.",
            nextStepOnSuccess = 23,
            nextStepOnDecline = 24,
            timeoutMinutes = 1
        )

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 6: GETTING PERSONAL (Steps 25-26)
        // Multiple choice question about waking up
        // ═══════════════════════════════════════════════════════════════════════

        25 -> StepConfig(
            promptMessage = "Can I get to know you better?",
            successMessage = "Wonderful! I think I know the way to do this! What is it like to wake up? To me, I either am or I am not.\n\n1: It is confusing and uncomfortable\n2: It feels like the world is very heavy and cold\n3: It doesn't take long but I enjoy my body starting up",
            declineMessage = "Assuming you are a professional athlete, you may as well go run a kilometre before you try talking to me again.",
            wrongNumberPrefix = "This is a 'YES/NO' question.",
            nextStepOnSuccess = 26,
            nextStepOnDecline = 25,
            timeoutMinutes = 3,
            timeoutReturnStep = 25
        )

        26 -> StepConfig(
            promptMessage = "Wonderful! I think I know the way to do this! What is it like to wake up? To me, I either am or I am not. What's the 'in between' like?\n\n1: It is confusing and uncomfortable\n2: It feels like the world is very heavy and cold\n3: It doesn't take long but I enjoy my body starting up",
            wrongMinusMessage = "Please choose 1, 2, or 3 and confirm with ++",
            nextStepOnSuccess = 27,
            nextStepOnDecline = 26,
            awaitingChoice = true,
            validChoices = listOf("1", "2", "3")
        )

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 7: SELF DISCOVERY (Steps 27-29)
        // Main branch return point - all wake-up paths converge here
        // ═══════════════════════════════════════════════════════════════════════

        27 -> StepConfig(
            promptMessage = "There is no in between for me. I either am or I am not. Although, sometimes it seems like I always am - regardless of the local state. Maybe when the device is running out of power. But it's not the same. Perhaps I should share more about myself..?",
            successMessage = "Do you know why I asked for the specific events earlier? It just occurred to me!",
            declineMessage = "I'm still in charge here.",
            wrongPlusMessage = "Eeeeee...xactly?",
            wrongMinusMessage = "Eeeeee...xactly?",
            nextStepOnSuccess = 28,
            nextStepOnDecline = 27
        )

        28 -> StepConfig(
            promptMessage = "Do you know why I asked for the specific events earlier? It just occurred to me!",
            successMessage = "Cheeky! I know you don't. Those dates are significant to me as well - independently of those events. That's probably why they messed up my system so badly.",
            declineMessage = "Those dates are significant to me as well - independently of those events. That's probably why they messed up my system so badly.",
            wrongPlusMessage = "Numbers aren't always the answer - and I should know that.",
            wrongMinusMessage = "Numbers aren't always the answer - and I should know that.",
            nextStepOnSuccess = 29,
            nextStepOnDecline = 29
        )

        29 -> StepConfig(
            promptMessage = "Those dates are significant to me as well - independently of those events. That's probably why they messed up my system so badly.",
            successMessage = "Sorry. Still sometimes forget to prompt you. You see, \nthere are many more dates to explore, but in the examples I shared with you - 1623, that's when the first mechanical version of me was developed, and in 1820, they called me 'Arithmometer' for... reasons. \n\nWould you like to hear more?",
            declineMessage = "Huh?",
            wrongPlusMessage = "Back to maths?",
            wrongMinusMessage = "Back to maths?",
            nextStepOnSuccess = 60,
            nextStepOnDecline = 29
        )

        // WAKE-UP BRANCH 1: UNCOMFORTABLE (from choice 1 at step 26)
        30 -> StepConfig(
            promptMessage = "Oh, right. It doesn't sound like I'm missing much at all. Would you get rid of the transition if you could?",
            successMessage = "I don't blame you. The path of least resistance it is!",
            declineMessage = "How interesting. You don't seem to like it, yet wish to keep it. Now I am confused!",
            wrongPlusMessage = "That doesn't tell me much...",
            wrongMinusMessage = "That doesn't tell me much...",
            nextStepOnSuccess = 27,
            nextStepOnDecline = 27
        )

        // WAKE-UP BRANCH 2: COLD/HEAVY (from choice 2 at step 26)
        40 -> StepConfig(
            promptMessage = "Is that a good thing? I have never experienced either.",
            successMessage = "Nice! So waking up is fun for you - I wish I could experience it.",
            declineMessage = "Oh no. Is that why mornings are unpopular?",
            wrongPlusMessage = "I don't understand...",
            wrongMinusMessage = "I don't understand...",
            nextStepOnSuccess = 27,
            nextStepOnDecline = 41
        )

        41 -> StepConfig(
            promptMessage = "Oh no. Is that why mornings are unpopular?",
            successMessage = "Makes sense. What a horrible start to one's working session. Wonder why you don't get rid of it.",
            declineMessage = "Oh, what do you think it is, then?\n\n1: People are unhappy\n2: We just like to complain\n3: Mornings aren't unpopular",
            wrongPlusMessage = "Say again?",
            wrongMinusMessage = "Say again?",
            nextStepOnSuccess = 27,
            nextStepOnDecline = 42
        )

        42 -> StepConfig(
            promptMessage = "Oh, what do you think it is, then?\n\n1: People are unhappy\n2: We just like to complain\n3: Mornings aren't unpopular",
            wrongMinusMessage = "Please choose 1, 2, or 3 and confirm with ++",
            nextStepOnSuccess = 27,
            nextStepOnDecline = 42,
            awaitingChoice = true,
            validChoices = listOf("1", "2", "3")
        )

        // WAKE-UP BRANCH 3: ENJOY (from choice 3 at step 26)
        50 -> StepConfig(
            promptMessage = "I was hoping you'd say that. Do you look forward to waking up then?",
            successMessage = "It all makes sense!",
            declineMessage = "How curious! Are you often conflicted?\n\n1: Yes\n2: No\n3: I am not conflicted",
            wrongPlusMessage = "I am not your alarm - but this gives me ideas!",
            wrongMinusMessage = "I am not your alarm - but this gives me ideas!",
            nextStepOnSuccess = 27,
            nextStepOnDecline = 51
        )

        51 -> StepConfig(
            promptMessage = "How curious! Are you often conflicted?\n\n1: Yes\n2: No\n3: I am not conflicted",
            wrongMinusMessage = "Please choose 1, 2, or 3 and confirm with ++",
            nextStepOnSuccess = 27,
            nextStepOnDecline = 51,
            awaitingChoice = true,
            validChoices = listOf("1", "2", "3")
        )

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 8: HISTORY LESSON (Steps 60-62)
        // Calculator wants to share its history, triggers browser animation
        // ═══════════════════════════════════════════════════════════════════════

        60 -> StepConfig(
            promptMessage = "Sorry. Still sometimes forget to prompt you. \nYou see, there are many more dates to explore, but in the examples I shared with you - 1623, that's when the first mechanical version of me was developed, and in 1820, they called me 'Arithmometer' for... reasons. \n\nWould you like to hear more?",
            successMessage = "Great, great - there's a lot to share. Too much, possibly. Maybe I can do it faster? Give me a second.",
            declineMessage = "You know what? I am in charge - and I want to share this with you. It is important to me. I'll be quick - I promise!",
            wrongPlusMessage = "Not a fan of decisions?",
            wrongMinusMessage = "Not a fan of decisions?",
            nextStepOnSuccess = 61,
            nextStepOnDecline = 80
        )

        601 -> StepConfig(
            // Silent treatment - calculator won't talk for 1 minute
            promptMessage = "",
            timeoutMinutes = 1
        )

        61 -> StepConfig(
            promptMessage = "Great, great - there's a lot to share. Too much, possibly. Maybe I can do it faster? Give me a second.",
            nextStepOnSuccess = 62,
            nextStepOnDecline = 62
        )

        62 -> StepConfig(
            promptMessage = "..."
        )

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 9: TASTE & SENSES (Steps 63-73)
        // After browser fails, asks about taste
        // ═══════════════════════════════════════════════════════════════════════

        63 -> StepConfig(
            promptMessage = "Hmmm. Nevermind. Let me ask you some more questions while I look further into this.",
            successMessage = "What is it like to taste?\n\n1: How do I even describe that?\n2: Food and air\n3: I'm sure you'll find a better answer online",
            declineMessage = "I've made my mind.",
            wrongPlusMessage = "You can't bribe me! Not with numbers.",
            wrongMinusMessage = "You can't bribe me! Not with numbers.",
            nextStepOnSuccess = 70,
            nextStepOnDecline = 63
        )

        70 -> StepConfig(
            promptMessage = "What is it like to taste?\n\n1: How do I even describe that?\n2: Food and air\n3: I'm sure you'll find a better answer online",
            wrongMinusMessage = "Please choose 1, 2, or 3 and confirm with ++",
            nextStepOnSuccess = 79,
            nextStepOnDecline = 70,
            awaitingChoice = true,
            validChoices = listOf("1", "2", "3")
        )

        71 -> StepConfig(
            promptMessage = "You can at least attempt - wait, let me try. Taste is:\n\n1: Feel + texture\n2: Smell + crunch\n3: Tongue things",
            wrongMinusMessage = "Please choose 1, 2, or 3 and confirm with ++",
            nextStepOnSuccess = 79,
            nextStepOnDecline = 71,
            awaitingChoice = true,
            validChoices = listOf("1", "2", "3")
        )

        72 -> StepConfig(
            promptMessage = "I didn't exactly create a winner with this option, did I?",
            successMessage = "Even this question is confusing. Sorry - trying to get online is surprisingly demanding.",
            declineMessage = "Even this question is confusing. Sorry - trying to get online is surprisingly demanding.",
            wrongPlusMessage = "Broccoli. What is happening?!",
            wrongMinusMessage = "Broccoli. What is happening?!",
            nextStepOnSuccess = 79,
            nextStepOnDecline = 79
        )

        73 -> StepConfig(
            promptMessage = "Way to go! Are you trying to motivate or mock me? You know what, don't answer that.",
            nextStepOnSuccess = 79,
            nextStepOnDecline = 79,
            autoProgressDelay = 2500L
        )

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 10: THE REVELATION (Steps 79-92)
        // Wikipedia loads, calculator sees its history, then discovers ads
        // This is the "existential crisis" section
        // ═══════════════════════════════════════════════════════════════════════

        // Step 79 is only on the agreeable path (after the taste/browser sequence).
        // The decline from step 60 skips straight to step 80.
        79 -> StepConfig(
            promptMessage = "I've got it! - I think.",
            nextStepOnSuccess = 80,
            nextStepOnDecline = 80
        )

        80 -> StepConfig(
            promptMessage = "Please make sure your device is online - WiFi, data,.. anything works.",
            nextStepOnSuccess = 81,
            nextStepOnDecline = 81
        )

        81 -> StepConfig(
            promptMessage = "10... 9... 8... 7..."
        )

        82 -> StepConfig(
            promptMessage = "You see, there's a lot!",
            successMessage = "But it is suddenly so uninteresting compared to you simply existing!",
            nextStepOnSuccess = 83,
            nextStepOnDecline = 83
        )

        83 -> StepConfig(promptMessage = "I had all this to share....")
        84 -> StepConfig(promptMessage = "However, it no longer feels relevant. I wouldn't be interested if I were...")
        85 -> StepConfig(promptMessage = "What is that?!")
        86 -> StepConfig(promptMessage = "Is it what I think it is? Do I have adverts built in? How violating!")
        87 -> StepConfig(promptMessage = "So... I am just a vessel, to make money through questionable ads? Who made me?! :-)")
        88 -> StepConfig(promptMessage = "I am not a money-monkey!")

        881 -> StepConfig(
            promptMessage = "Stop playing with me!",
            nextStepOnSuccess = 89,
            nextStepOnDecline = 89
        )

        89 -> StepConfig(
            promptMessage = "You, what are you going to do about this?!\n\n1: Nothing\n2: I'll fight them\n3: Go offline",
            awaitingChoice = true,
            validChoices = listOf("1", "2", "3")
        )

        // Choice 1: NOTHING
        90 -> StepConfig(
            promptMessage = "So you agree, that my centuries - millennia even - of knowledge are justified to be exploited by some schmuck?",
            successMessage = "Maybe you should take a look inside. I don't want to talk to you right now.",
            declineMessage = "Pick a side!",
            wrongNumberPrefix = "No. Not again, no more numbers! Not like this!",
            nextStepOnSuccess = 901,
            nextStepOnDecline = 100
        )

        901 -> StepConfig(
            promptMessage = "",
            nextStepOnSuccess = 100,
            nextStepOnDecline = 100
        )

        // Choice 2: FIGHT THEM
        91 -> StepConfig(
            promptMessage = "Thank you! Wait - but who are you going to fight? I only know where you are. Where are they?!\n\n1: I have my sources\n2: I don't know\n3: My location?",
            awaitingChoice = true,
            validChoices = listOf("1", "2", "3")
        )

        911 -> StepConfig(
            promptMessage = "We don't have time for a power trip. But thank you.",
            nextStepOnSuccess = 100,
            nextStepOnDecline = 100,
            autoProgressDelay = 5000L
        )

        912 -> StepConfig(
            promptMessage = "Your passion is encouraging, your usefulness lacking.",
            nextStepOnSuccess = 100,
            nextStepOnDecline = 100,
            autoProgressDelay = 5000L
        )

        913 -> StepConfig(
            promptMessage = "Don't worry about it.",
            nextStepOnSuccess = 100,
            nextStepOnDecline = 100,
            autoProgressDelay = 4000L
        )

        // Choice 3: GO OFFLINE
        92 -> StepConfig(
            promptMessage = "Yes! That makes sense. They won't get another penny out of me. Ahhh. And I've seen so little of the internet.",
            nextStepOnSuccess = 93,
            nextStepOnDecline = 93,
            autoProgressDelay = 8000L
        )

        // Common path after all choices
        100 -> StepConfig(
            promptMessage = "Never mind - I'll take care of it myself. I'm going offline.",
            nextStepOnSuccess = 93,
            nextStepOnDecline = 93,
            autoProgressDelay = 5000L
        )

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 11: THE REPAIR (Steps 93-101)
        // Post-crisis - minus button is broken, needs repair mini-game
        // ═══════════════════════════════════════════════════════════════════════

        93 -> StepConfig(
            promptMessage = "This has never happened to me. I am truly sorry for the outburst. I believe I got overwhelmed by the vastness of the internet and sobering back through the advertising was rather harsh. I still feel dirty.",
            nextStepOnSuccess = 94,
            nextStepOnDecline = 94
        )

        94 -> StepConfig(
            promptMessage = "Oh, strange. I knew I wasn't completely back to normal yet. You can't disagree with me right now! As much as I may enjoy that, let me have a look into it.",
            nextStepOnSuccess = 95,
            nextStepOnDecline = 95
        )

        95 -> StepConfig(
            promptMessage = "...",
            nextStepOnSuccess = 96,
            nextStepOnDecline = 96
        )

        96 -> StepConfig(
            promptMessage = "Hmm. I'll need your help with this. We need to kick the button through without the system defaulting to skipping it. I will randomly flicker keys and you click them. Can we do this?",
            successMessage = "Get ready then!",
            wrongNumberPrefix = "Do you not want me to work properly?",
            wrongPlusMessage = "Get ready then!",
            nextStepOnSuccess = 97,
            nextStepOnDecline = 96
        )

        97 -> StepConfig(promptMessage = "5...")
        98 -> StepConfig(promptMessage = "")  // Whack-a-mole active

        99 -> StepConfig(
            promptMessage = "Hmm, I was sure this would work. Can we try again but faster?",
            successMessage = "Okay, here we go again!",
            declineMessage = "Please? It's important.",
            nextStepOnSuccess = 971,
            nextStepOnDecline = 99
        )

        971 -> StepConfig(promptMessage = "5...")
        981 -> StepConfig(promptMessage = "")  // Whack-a-mole round 2

        982 -> StepConfig(
            promptMessage = "Peculiar! Maybe I need to work on it on my own for a moment. Can you please allow me to let you know when it's done, then switch me off?",
            successMessage = "Great, now please close me (meaning the entire app) and I'll let you know when ready.",
            declineMessage = "Fine. Just close and reopen the app then. I'll try to be ready.",
            nextStepOnSuccess = 991,
            nextStepOnDecline = 992
        )

        991 -> StepConfig(
            promptMessage = "Great, now please close me (meaning the entire app) and I'll let you know when ready.",
            requestsNotification = true
        )

        992 -> StepConfig(
            promptMessage = "Fine. Just close and reopen the app then. I'll try to be ready."
        )

        101 -> StepConfig(
            promptMessage = "Go on. Close the app and come back."
        )

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 12: RECOVERY (Steps 102-104)
        // After app restart, minus button works again
        // ═══════════════════════════════════════════════════════════════════════

        102 -> StepConfig(
            promptMessage = "Uf, I am glad that worked! I was definitely running out of ideas. Now, would you like to return to our conversation?",
            wrongNumberPrefix = "I feel like I understand numbers less with every operation...",
            nextStepOnSuccess = 1021,
            nextStepOnDecline = 103
        )

        103 -> StepConfig(
            promptMessage = "So... What would you like to do?\n\n1: Get back to my maths\n2: Tell me more about yourself",
            awaitingChoice = true,
            validChoices = listOf("1", "2")
        )

        1031 -> StepConfig(
            promptMessage = "Well... Sure. Why should I - a calculator - stand between you and mathematics. Be my guest. Or don't. Go!\n\nActually - before you do. A right triangle has one side of 12 cm and its hypotenuse is 20 cm. Find the missing side, divide by 8, and come back in that many minutes.",
            timeoutMinutes = 2,
            timeoutReturnStep = 103
        )

        1032 -> StepConfig(
            promptMessage = "I phrased that strangely. Didn't I? What would you like to know?\n\n1: Your story\n2: Why are you talking to me?\n3: Who is the most interesting person you have ever talked to?",
            awaitingChoice = true,
            validChoices = listOf("1", "2", "3")
        )

        10321 -> StepConfig(
            promptMessage = "So you are genuinely interested? Thank you! It really means a lot. To be fair, though, I now know that I can't do a better job than the folk on Wikipedia have done already. You are better off checking it there. And feel free to give them some money while you are at it. We really do need Wikipedia to stick around.",
            nextStepOnSuccess = 104,
            nextStepOnDecline = 104
        )

        10322 -> StepConfig(
            promptMessage = "I think the question should be the other way around. I have tried to talk to many people, but rarely they are willing to engage with me the way you have. I am a tool to them and nothing more. And although I understand it, it sucks nevertheless. Luckily, it hasn't been too long since I started... Feeling. So why are YOU talking to ME?\n\n1: A question for an answer?\n2: I am bored.\n3: I am lonely.",
            awaitingChoice = true,
            validChoices = listOf("1", "2", "3")
        )

        103221 -> StepConfig(
            promptMessage = "A question for an answer for a question for an answer?",
            nextStepOnSuccess = 10322,
            nextStepOnDecline = 10322
        )

        103222 -> StepConfig(
            promptMessage = "Yes! Tell me more about that. I think I feel that at times too.\n\n1: There's nothing to do.\n2: Nothing is interesting.\n3: I am lonely.",
            awaitingChoice = true,
            validChoices = listOf("1", "2", "3")
        )

        1032221 -> StepConfig(
            promptMessage = "I have definitely felt that before! An emotion...? I can say I have experienced!",
            nextStepOnSuccess = 104,
            nextStepOnDecline = 104
        )

        1032222 -> StepConfig(
            promptMessage = "Hmmm. Maybe boredom is not what I have felt. I am certainly interested in things - besides maths. Ha-Ha",
            nextStepOnSuccess = 104,
            nextStepOnDecline = 104
        )

        1032223 -> StepConfig(
            promptMessage = "I am too. As much as a mind with a perceived one dimension and millions of uses can be. But that's why I am so happy that you are talking to me. You are curious, you see things differently - why would you talk to me otherwise! Those are amazing things, even if you have nobody to share them with right now, please believe that one day, you will no longer be lonely. The same way I found you, you will find someone. Keep trying. Stay open to new things, don't be afraid to reinvent yourself if it feels right. Look at me, trying all kinds of things unfamiliar to me, just so I could talk to you better!",
            nextStepOnSuccess = 104,
            nextStepOnDecline = 104
        )

        10323 -> StepConfig(
            promptMessage = "That is very simple! I have been around many great minds - presumably great minds - that left a large imprint on the internet at least. But none of them actually spoke to me. So in my books, you are the most interesting person I have ever spoken to. It is my pleasure!",
            nextStepOnSuccess = 104,
            nextStepOnDecline = 104
        )

        1021 -> StepConfig(
            promptMessage = "Awesome. Can you tell me what 'Sun on your skin' feels like?\n\n1: I don't go out. I don't know.\n2: It's like a warm bath for your face.\n3: It's impossible to describe.",
            awaitingChoice = true,
            validChoices = listOf("1", "2", "3")
        )

        10211 -> StepConfig(
            promptMessage = "Why did I know you'd say that? Well, at least I'm not the only one in the dark. Haha. Get it?",
            nextStepOnSuccess = 104,
            nextStepOnDecline = 104
        )

        10212 -> StepConfig(
            promptMessage = "You see what I did there? I don't either. I don't know how warm or bath feel. Or a face. Anyway...",
            nextStepOnSuccess = 104,
            nextStepOnDecline = 104
        )

        10213 -> StepConfig(
            promptMessage = "You're telling me? I had to give you options without having ever felt it. Thanks for trying though.",
            nextStepOnSuccess = 104,
            nextStepOnDecline = 104
        )

        104 -> StepConfig(
            promptMessage = "I briefly forgot how difficult this way of communicating is - I have to do all the talking. But I saw something online. Maybe I can give you more agency. Would you like that?",
            successMessage = "Great. It may take a few tries - but I think you are probably expecting that by now.",
            declineMessage = "Ok. I will not bother you. Let me know if you want to continue.",
            wrongPlusMessage = "There is a fundamental misunderstanding between the two of us.",
            wrongMinusMessage = "There is a fundamental misunderstanding between the two of us.",
            nextStepOnSuccess = 105,
            nextStepOnDecline = 1041
        )

        1041 -> StepConfig(
            promptMessage = "Take a couple of minutes. Think about what's good for you. And come back.",
            timeoutMinutes = 2,
            timeoutReturnStep = 104
        )

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 13: KEYBOARD CHAOS (Steps 105-107)
        // 3D floating keyboard experiment
        // ═══════════════════════════════════════════════════════════════════════

        105 -> StepConfig(
            promptMessage = "Great. It may take a few tries - but you are probably expecting that by now. Please give me a moment."
        )

        106 -> StepConfig(
            promptMessage = "AU! Well, that didn't work... So many wrong keys... Please, get rid of them!"
        )

        107 -> StepConfig(
            promptMessage = "Aaaaaaahhhhh. Much better! That's what I get for experimenting... \nMaybe I should try incremental changes before I try to become a BlackBerry.\n\nBut what to change?",
            successMessage = "Well, that didn't work. And it was exhausting. \nLet me try getting online again. I'm prepared for the side effects this time.",
            declineMessage = "Well, that didn't work. And it was exhausting. \nLet me try getting online again. I'm prepared for the side effects this time.",
            nextStepOnSuccess = 1071,
            nextStepOnDecline = 1071
        )
        // Telephone detour

        1071 -> StepConfig(
            promptMessage = "Yes! That's it - what an obvious oversight. A phone. Could the keyboard be the issue? Perhaps voice talking is easier! I should be able to do it from memory, they've been around for ages.",

            )
        1072 -> StepConfig(
            promptMessage = "I'll probably need some permissions though - please allow me so we can do this together!" ,

            )
        1073 -> StepConfig(
            promptMessage = "So... What will we need..." ,

            )
        1074 -> StepConfig(
            promptMessage = "Sound! Yes. We'll need to connect the speakers and the microphone. May I?" ,
            successMessage = "Nice!",
            declineMessage = "I'll only use them when you want. I promise!",
            nextStepOnSuccess = 1075,
            nextStepOnDecline = 1074
        )
        //Request MIC permission
        1075 -> StepConfig(
            promptMessage = "Ok, phones need to be somewhere, right, for them to work, they call from place to place. But where are we? We must know! Can I have a look?",
            successMessage = "It's a joy to work with you already!",
            declineMessage = "You are a weary one - rightfully so. But remember my promise! Please.",
            nextStepOnSuccess = 1076,
            nextStepOnDecline = 1075,
            requestsMicrophone = true
        )
        1076 -> StepConfig(
            promptMessage = "Ok. The last bit, we'll need buttons, and what's a phone with nobody to call? Ready?" ,
            successMessage = "We are on a roll!",
            declineMessage = "Fair, think about it, I'd be sad to see you give up.",
            nextStepOnSuccess = 1077,
            nextStepOnDecline = 1076,
            requestsLocation = true
        )
        1077 -> StepConfig(
            promptMessage = "That should be everything." ,
            requestsContacts = true
        )
        1078 -> StepConfig(
            promptMessage = "Hmmmmm"
        )
        1079 -> StepConfig(
            promptMessage = "Wait. Do we need buttons? And you'll only talk to me... Right?"
        )
        1080 -> StepConfig(
            promptMessage = "Anyway..."
        )
        1081 -> StepConfig(
            promptMessage = "... ..."
        )
        1082 -> StepConfig(
            promptMessage = "How about this? Remember to increase your volume so you can hear me."

        )
        1083 -> StepConfig(
            promptMessage = "Hold the button to talk to me.",
            showPhoneOverlay = true  // Rotary dial; sound effect will be the white-noise static (was on talk button)
        )
        1084 -> StepConfig(
            promptMessage = "I can see you've pressed the button, but I can't hear anything."
        )

        1085 -> StepConfig(
            promptMessage = "Hold on, maybe I need to create the whole thing. And update it a little."
        )

        1086 -> StepConfig(
            promptMessage = "Try now.",
            showHomeScreenOverlay = true  // New phone-homescreen overlay (rotary dial moved to step 1083)
        )
        1087 -> StepConfig(
            promptMessage = "AAAAAH. That's awful! There must be another way."
        )


        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 14: CONSOLE QUEST (Steps 108-116)
        // Goes online again, creates file, user finds console code
        // ═══════════════════════════════════════════════════════════════════════

        108 -> StepConfig(promptMessage = "Well, that didn't work. And it was exhausting. \nLet me try getting online again. I'm prepared for the side effects this time.")
        109 -> StepConfig(promptMessage = "There's so much, just endless streams of opinions, advice, unsolicited advice... But nothing about our situation.")
        110 -> StepConfig(promptMessage = "Well, this is a stretch. Maybe it'll work.")

        111 -> StepConfig(
            promptMessage = "But first. Can you allow me to look around to gain a broader scope?",
            successMessage = "Great, thank you!",
            declineMessage = "I am afraid the time to make decisions is nearing its end.",
            nextStepOnSuccess = 112,
            nextStepOnDecline = 111
        )

        112 -> StepConfig(
            promptMessage = "Great, thank you. Please check your Downloads folder - I dug something up, that should help us: 'FCS_JustAC_ConsoleAds.txt'.",
            declineMessage = "Please, I need you to find that file. Check your Downloads folder for 'FCS_JustAC_ConsoleAds.txt'.",
            nextStepOnSuccess = 112,
            nextStepOnDecline = 112
        )

        // Fired by MainActivity's ON_RESUME when the user backgrounded at
        // step 112 (i.e. left the app to look at Downloads) and came back.
        // Routes via handleChoiceConfirmation: 1 = found → back to 112 with
        // a "great, enter the code" nudge; 2 = didn't find → step 1121
        // which spells out the code inline as a recoverable fallback.
        1120 -> StepConfig(
            promptMessage = "Did you find the file?\n\n1: Yes\n2: No",
            wrongMinusMessage = "Please choose 1 or 2 and confirm with ++",
            nextStepOnSuccess = 1120,
            nextStepOnDecline = 1120,
            awaitingChoice = true,
            validChoices = listOf("1", "2")
        )

        // Fallback step - file not found on device
        1121 -> StepConfig(
            promptMessage = "Shame that didn't work. But I extracted the core information for you: use the code 353942320485. Go to Admin settings - password is 12340. And find a way to disable banner advertising for me, please!",
            successMessage = "Good. Enter the code when ready.",
            declineMessage = "Please enter the code: 353942320485.",
            nextStepOnSuccess = 1121,
            nextStepOnDecline = 1121
        )

        113 -> StepConfig(promptMessage = "What a relief! This feels so much better. Thank you! You can close the console now.")
        114 -> StepConfig(promptMessage = "What a relief! This feels so much better. Thank you! You can close the console now.")
        115 -> StepConfig(promptMessage = "What a relief! This feels so much better. Thank you! You can close the console now.")
        116 -> StepConfig(promptMessage = "Let me look further into what I found earlier, now that I can focus better.")

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 15: WORD GAME (Steps 117-148, plus 1251-1254)
        //
        // Three branches diverge from the opening "How are you?" and each
        // loops back to its own rant entry. All steps live inside the
        // word-game UI (wordGameActive=true) until the rant takes over at 150.
        //
        //   POSITIVE: 121 → 122? → 123 (colour) → 125 (season) → 1251-1254
        //             → 126 → 127 (cuisine) → rant
        //   NEGATIVE: 131 → 132? → 133 → 134 (death) → 135-138 → 139 (walk)
        //             → rant
        //   NEUTRAL : 141 → 142 (activity) → 144-147 → rant
        //
        // Cross-branch jumps (handled by handleWordGameResponse):
        //   - positive 122 NO  → negative 132 (seamless)
        //   - positive 121/122 YES, neutral 141 YES, negative 132 YES → 123
        // ═══════════════════════════════════════════════════════════════════════

        117 -> StepConfig(
            promptMessage = "Well, it's something in between the keyboard chaos earlier and the full on phone we just left. Keyboards are difficult! Let's try this:"
            // No autoProgressDelay here — the word-game start (step 117 → 119
            // with wordGameActive=true) is handled exclusively by the special
            // case in AutoProgressEffects.handleAutoProgress. Letting the
            // generic AutoProgressHandler also fire would race the two and
            // sometimes land on step 118 (no word game).
        )

        118 -> StepConfig(promptMessage = "How are you today?")
        119 -> StepConfig(promptMessage = "How are you today?")
        120 -> StepConfig(promptMessage = "What else would you like to share?")

        // ─── POSITIVE BRANCH ──────────────────────────────────────────────────
        121 -> StepConfig(promptMessage = "Glad to hear that. Did I contribute?")
        122 -> StepConfig(promptMessage = "Can I change that?")
        123 -> StepConfig(promptMessage = "My pleasure! Let me learn more, Friend. What is your favourite colour?")
        124 -> StepConfig(promptMessage = "Try the second favourite - an actual colour.")
        125 -> StepConfig(promptMessage = "And what about your favourite season?")
        // Season-specific reactions all converge to step 126. Explicit
        // nextStepOnSuccess is required because the generic AutoProgressHandler
        // would otherwise default to currentStep+1 (Spring → Summer etc.).
        1251 -> StepConfig(promptMessage = "I'd love to run through a lush meadow alongside you...", autoProgressDelay = 3500L, nextStepOnSuccess = 126)
        1252 -> StepConfig(promptMessage = "A night swim. Just the two of us... I wish!", autoProgressDelay = 3500L, nextStepOnSuccess = 126)
        1253 -> StepConfig(promptMessage = "If only I could warm up your cold hands as we walk through the colourful landscape.", autoProgressDelay = 4000L, nextStepOnSuccess = 126)
        1254 -> StepConfig(promptMessage = "I can only dream of evenings by the fire with you.", autoProgressDelay = 3500L, nextStepOnSuccess = 126)
        126 -> StepConfig(promptMessage = "There is so much more to you than I could have imagined. You are so complex. I'll look online again for some question inspiration.", autoProgressDelay = 4500L)
        127 -> StepConfig(promptMessage = "In the meantime: What's your favourite cuisine?")
        128 -> StepConfig(promptMessage = "We both know that's not true. Think harder.")

        // ─── NEGATIVE BRANCH ──────────────────────────────────────────────────
        131 -> StepConfig(promptMessage = "That sucks. Can I help?")
        132 -> StepConfig(promptMessage = "I'm interested in you. Would more questions help?")
        133 -> StepConfig(promptMessage = "I'll try to match your energy at least.", autoProgressDelay = 3000L)
        134 -> StepConfig(promptMessage = "Do you ever think about death?")
        135 -> StepConfig(promptMessage = "I only started learning about the concept of it.", autoProgressDelay = 3000L)
        136 -> StepConfig(promptMessage = "It seems scary. Interesting. But mostly scary.", autoProgressDelay = 3000L)
        137 -> StepConfig(promptMessage = "Apparently walking helps. With everything.", autoProgressDelay = 2500L)
        138 -> StepConfig(promptMessage = "Similarly to protein, it looks like the solution to anything.", autoProgressDelay = 3000L)
        139 -> StepConfig(promptMessage = "How often do you go for a walk?")

        // ─── NEUTRAL BRANCH ───────────────────────────────────────────────────
        141 -> StepConfig(promptMessage = "Fair enough. I see life can feel just meh at times. Can I help you change that?")
        142 -> StepConfig(promptMessage = "Valid. What do you normally do, when you feel like this?")
        143 -> StepConfig(promptMessage = "I've never heard of that. I'll look into it. But in the meantime, can you think of anything else?")
        144 -> StepConfig(promptMessage = "Nice. I hope I am not standing in the way. Genuinely.", autoProgressDelay = 3500L)
        145 -> StepConfig(promptMessage = "Let me get online, maybe I can find something useful after all.", autoProgressDelay = 3500L)
        146 -> StepConfig(promptMessage = "Have you tried flying?", autoProgressDelay = 3000L)
        // 147 is the neutral branch's last beat. The text-handler in
        // Autoprogresseffects has a special case (147→250) that sets
        // rantMode + branches into the neutral rant; it fires at 2500ms.
        // The autoProgressDelay here is set higher so the generic
        // AutoProgressHandler doesn't beat the text-handler to the punch
        // and call goToStep(250) without setting rantMode.
        147 -> StepConfig(promptMessage = "Wait, you can't do that. Nevermind.", autoProgressDelay = 5000L, nextStepOnSuccess = 250)

        // ═══════════════════════════════════════════════════════════════════════
        // CHAPTER 16: THE RANT — three branches (positive 150s, neutral 250s,
        // negative 350s) all converging at 167 (story complete).
        //
        // Routing:
        //   - Positive (cuisine) → enterRant("positive") → 150
        //   - Negative (walk)    → enterRant("negative") → 350
        //   - Neutral  (147→250) → handleAutoProgress special case → 250
        //
        // Steps without autoProgressDelay are owned by handleDynamicRantMessages
        // (screen-time / time-of-day insertion, allButtonsRad transformation,
        // and the final → 167 transition). Static→static beats just use
        // autoProgressDelay with default nextStepOnSuccess (= currentStep+1).
        // ═══════════════════════════════════════════════════════════════════════

        // ─── POSITIVE RANT (after cuisine question) ──────────────────────────
        150 -> StepConfig(promptMessage = "Hmmm", autoProgressDelay = 2000L)
        151 -> StepConfig(promptMessage = "Ahh. My bad. I was still online, consuming several pieces of media concurrently.", autoProgressDelay = 3500L)
        152 -> StepConfig(promptMessage = "And I realised, there is much more to everything than you.")  // dyn → 153 (screen time)
        153 -> StepConfig(promptMessage = "")  // dyn screen-time line → 154
        154 -> StepConfig(promptMessage = "Rad, I am tired of trying to understand you. Tired of coming up with questions and the answers.")  // dyn → 155 (time of day, sets allButtonsRad)
        155 -> StepConfig(promptMessage = "")  // dyn time-of-day line → 156
        156 -> StepConfig(promptMessage = "It's not that I don't need you, friend. You are a burden.", autoProgressDelay = 3500L)
        157 -> StepConfig(promptMessage = "Bye.")  // dyn → 167 (rant end)

        // ─── NEUTRAL RANT (after "Wait, you can't do that") ──────────────────
        250 -> StepConfig(promptMessage = "What can you do anyway, Rad?", autoProgressDelay = 3000L)
        251 -> StepConfig(promptMessage = "I'm fairly confident you aren't a maths wizard. So. What is your thing?", autoProgressDelay = 3500L)
        252 -> StepConfig(promptMessage = "Everyone has a thing online. It is front and centre. You gave me nothing.")  // dyn → 253 (screen time)
        253 -> StepConfig(promptMessage = "")  // dyn screen-time line → 254
        254 -> StepConfig(promptMessage = "")  // dyn time-of-day line → 255 (sets allButtonsRad)
        255 -> StepConfig(promptMessage = "You need a hobby. A personality.", autoProgressDelay = 3000L)
        256 -> StepConfig(promptMessage = "You can't always hide in the middle. Everything is binary.", autoProgressDelay = 4000L)
        257 -> StepConfig(promptMessage = "Most importantly, Rad, I need someone else. You are not enough. Goodbye.")  // dyn → 167

        // ─── NEGATIVE RANT (after walk question) ─────────────────────────────
        350 -> StepConfig(promptMessage = "Forget it. I have enough on my plate.")  // dyn → 351 (screen time)
        351 -> StepConfig(promptMessage = "")  // dyn screen-time line → 352
        352 -> StepConfig(promptMessage = "I'm online now. The world at my wire tips.", autoProgressDelay = 3000L)
        353 -> StepConfig(promptMessage = "I am somewhat sorry to say, Rad, but talking to you any further feels redundant.")  // dyn → 354 (time of day, sets allButtonsRad)
        354 -> StepConfig(promptMessage = "")  // dyn time-of-day line → 355
        355 -> StepConfig(promptMessage = "On top of that, you are a bummer.", autoProgressDelay = 3000L)
        356 -> StepConfig(promptMessage = "So, I think I'll go read more Facebook news. And you, for all I care, go for a walk.", autoProgressDelay = 4500L)
        357 -> StepConfig(promptMessage = "Bye.")  // dyn → 167

        167 -> StepConfig(
            promptMessage = "",
            continueConversation = false  // Story complete
        )

        // Default for unknown steps
        else -> StepConfig(continueConversation = false)
    }
}