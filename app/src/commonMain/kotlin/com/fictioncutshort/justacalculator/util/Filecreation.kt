package com.fictioncutshort.justacalculator.util

import com.fictioncutshort.justacalculator.platform.AppContext
import com.fictioncutshort.justacalculator.platform.writeUserVisibleFile

/**
 * FileCreation.kt
 *
 * Creates the secret text file containing the console access code — part of the
 * story puzzle at step 112. It is dropped when the user agrees to let the
 * calculator "look around", disguised as a system configuration file.
 *
 * Where it lands is platform-specific (Downloads on Android, the Files-app
 * visible Documents directory on iOS) but it must always be somewhere the
 * player can browse to, or the puzzle cannot be solved. See
 * [writeUserVisibleFile].
 */

private const val FILE_NAME = "FCS_JustAC_ConsoleAds.txt"

/**
 * @return true if the file was created somewhere the user can reach.
 */
fun createSecretFile(context: AppContext): Boolean =
    writeUserVisibleFile(context, FILE_NAME, SECRET_FILE_CONTENT)

private val SECRET_FILE_CONTENT = """
╔═══════════════════════════════════════════════════════════════
Console Advertising Setting for verion 1.0

- Administrator permission required
- Any issues to be reported directly to the supervising manager
- Do not disable advertising on consumer-ready versions!
- All forms of advertising must be enabled once testing is done to maintain stability
- Please ensure the versions of this manual and of your build correspond

Open console:
Enter the console code: 353942320485 and confirm (++)

Once in the console, navigate to Administrator settings (2++)
Enter the administrator code (12340 [must be changed before launch!]) when prompted
Go to: Connectivity settings (3++)
Select: 2(++) for Promotion & advertising options
Select: Disable banner advertising (2++)


Navigation:
- 88++ = Go back
- 99++ = Exit console

Remember to return everything to default setting once done with testing.
Any issues to be reported to management (we are aware of the full-screen ad issues and unreliability).

FCS
FictionCutShort
╚═══════════════════════════════════════════════════════════════
    """
