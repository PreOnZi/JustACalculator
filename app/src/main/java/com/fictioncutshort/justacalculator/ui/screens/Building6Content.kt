package com.fictioncutshort.justacalculator.ui.screens

// ═══════════════════════════════════════════════════════════════════════════════
//  BUILDING 6 — STAGE 3 "REQUESTS" CONTENT
//
//  All the request copy lives here so it can be edited in one place. The runner
//  (`Building6Runner.kt`) walks `REQUESTS` in order; the team-mate name in MONEY
//  prompts is filled in at runtime from the player's contacts.
//
//  TODO(user): real message / text-exchange copy is supplied later — these are
//  placeholders only.
// ═══════════════════════════════════════════════════════════════════════════════
object Building6Content {

    enum class ReqType { MESSAGE, CALL, PERSON, MONEY }

    /** What happens when the player runs through the ACCEPT gate. */
    enum class Outcome { SLOW_TEXT, TURN_BACK, BRANCH_HELP, INTERACT, SEND_MONEY }

    enum class HelpKind { FIGHT, CLIMB, JUMP }

    class Request(
        val type: ReqType,
        val prompt: String,
        val accept: Outcome,
        val helpKind: HelpKind = HelpKind.CLIMB,
        val moneyCost: Int = 0,
        val textExchange: List<String> = emptyList(),
        // Money asks aren't "help" asks — declining one is not a refusal to help,
        // so it doesn't count toward the Stage-4 standing.
        val isHelpRequest: Boolean = true,
    )

    val REQUESTS: List<Request> = listOf(
        Request(
            ReqType.MESSAGE, "Can you help me move on Saturday?", Outcome.SLOW_TEXT,
            textExchange = listOf("You free Saturday?", "Maybe…", "Pleeease?", "Fine, I'll come"),
        ),
        Request(ReqType.CALL, "Quick favour — can you give me a hand?", Outcome.BRANCH_HELP, helpKind = HelpKind.CLIMB),
        Request(ReqType.PERSON, "Could you help me with this?", Outcome.INTERACT),
        Request(ReqType.MONEY, "needs £4", Outcome.SEND_MONEY, moneyCost = 4, isHelpRequest = false),
        Request(ReqType.MESSAGE, "Coming to help with the fence?", Outcome.TURN_BACK),
        Request(ReqType.CALL, "Can you back me up?", Outcome.BRANCH_HELP, helpKind = HelpKind.FIGHT),
    )
}
