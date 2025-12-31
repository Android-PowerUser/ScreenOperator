## Screen Operator
### Operates the screen with AI
This Android app operates the screen with commands from vision LLMs



#### • Like Computer use and Operator but rather Smartphone use for Android

#### • Can also control the Browser like Project Mariner and Browser use

<img src="https://github.com/Android-PowerUser/ScreenOperator/blob/main/Screenshot_20250526-192615_Screen%20Operator.png" alt="" width="141"/> <img src="https://github.com/Android-PowerUser/ScreenOperator/blob/main/Screenshot_20250802-231135_Screen%20Operator.png" alt="" width="141"/>

### Download and install
[Screen Operator v1.2.apk](https://github.com/Android-PowerUser/ScreenOperator/releases/download/v2025.8.13/Screen.Operator.v1.2_don_t_work_on_Android_13-.apk)

**Doesn't work on Android 14 and below. This will maybe fixed soon (see below)**

Updates in Github are much faster than on the Play Store and have no restrictions.

## Develop Screen Operator with AI


This app urgently needs an update. Google's interfaces (API's) have changed, and it should also use an omni-model that can actually see the screen content, not just receive a description. However, I am no longer active as a developer, but that doesn't mean it's over, because this app can be developed entirely by AI and therefore its development can be continued by anyone:

The code for each application is located in the /app/ folder on GitHub. To have it programmed for free, I use [jules.google.com](https://jules.google.com). You can connect it directly to GitHub. Gemini 2.5 Pro is free, and the limit is so high that I've never reached it. Gemini 3 Pro costs money, but you can use it for a month free and cancel before any charges apply. You should definitely go to the planning section on the rocket icon, because AI instructions surprisingly often implement something different than intended, and planning improves the performance of LLMs. Since Gemini isn't generally very good, I gave Jules the task. Jules looks at the code and tells me which files it needs to change to achieve the goal. You copy these files into lmarena.ai, select "direct," then Claude Opus 4.5 thinking, copy the task from Jules, paste the output from Claude unchanged into Jules to insert the new code snippets in the files and Gemini 2.5 Pro also very well recognizes where it belongs and automatically replaces the corresponding parts.

To build the app from the code, I've prepared GitHub action workflows. Stay in your fork, on your user account (you won't be able to start it otherwise), on mobile, click the gear icon and then Actions, and on desktop, click Actions directly. Click Workflows, select Android build, and start your chosen branch. After about 5 minutes, your app will be ready! If compilation errors occur, copy the output to Jules. If there are no more than 5 errors, even Gemini 2.5 Pro can usually handle this very reliably. Otherwise, proceed as you would when programming: Jules, then Claude, then Jules again.

After that, the app needs to be signed. I use MiXplorer and select the test signature option. The others don't always work as well or as quickly. You can install it now.

You are allowed to disable the paywall for developers using a "boolean variable" (hardcoded 1 or 0) and push it to my main branch. You be allowed to edit the app as long as you are attempting to push the updated code to this repository. Since I now only have an administrative role at Screen Operator, I no longer necessarily need to monetize it. In retrospect, a half-hour trial period was too restrictive. 


Some models no longer work because Google is changing the interfaces (API). You can usually find the current API names at [aistudio.google.com](https://aistudio.google.com), but you might not find them all there.


According to Google, the following issue exists with Android 14-: 
1 issue requires your attention

Kotlin incompatibilities cause crashes
Your app uses the Kotlin extension functions removeFirst() and removeLast(), which cause conflicts with Java functions on Android 15. This leads to crashes for apps on devices running Android 14 or lower. Your app uses these functions in the following places:

com.google.ai.sample.feature.multimodal.PhotoReasoningChatState.replaceLastPendingMessage
com.google.ai.sample.feature.multimodal.PhotoReasoningViewModel$aiResultReceiver$1.onReceive
com.google.ai.sample.feature.multimodal.PhotoReasoningViewModel.onStopClicked
To avoid crashes, replace all Kotlin calls to removeFirst() and removeLast() with removeAt(0) and removeAt(list.lastIndex).

Free omni models accessible via an API can be found [here](https://github.com/cheahjs/free-llm-api-resources)




### Video
[First attempt ever is recorded](https://m.youtube.com/watch?v=o095RSFXJuc)

<br/>

#### Note



If you in your Google account identified as under 18, you need an adult account because Google is (unreasonably) denying you the API key.

Preview models will eventually be removed by Google and unfortunately won't be redirected to finished equivalents. If this happens, please change the API in the code.
