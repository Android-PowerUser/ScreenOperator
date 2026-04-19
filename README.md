## Screen Operator
### Operates the screen with AI
This Android app operates the screen with commands from vision LLMs.


#### • The first Android agent app in the world (since June/2025)



<img src="https://github.com/Android-PowerUser/ScreenOperator/blob/main/Screenshot_20250526-192615_Screen%20Operator.png" alt="" width="141"/> <img src="https://github.com/Android-PowerUser/ScreenOperator/blob/main/Screenshot_20250802-231135_Screen%20Operator.png" alt="" width="141"/>

### Download and install
[Screen Operator v1.2.apk](https://github.com/Android-PowerUser/ScreenOperator/releases/download/v2025.8.13/Screen.Operator.v1.2_don_t_work_on_Android_14-.apk) or, if you're logged in in Github, [nightly builds](https://github.com/Android-PowerUser/ScreenOperator/actions) from Github actions.

**Doesn't work on Android 14 and below. This will maybe fixed soon (see below)**

Updates in Github are much faster than on the Play Store and have no restrictions.

## Develop Screen Operator with AI


This app urgently needs an update. Google's interfaces (API's) have changed, and it should also use shared parameter model (shared backbone), that can actually see the screen content, not just receive a description. However, I am no longer active as a developer, but that doesn't mean it's over, because this app can be developed entirely by AI and therefore its development can be continued by anyone:

To vibe coding for free I use [Codex](https://ChatGPT.com/codex). You can connect it directly to GitHub. You will probably have to fork the project first so that you can edit it. It has a free quota that will be weekly refreshed and you can also easy switch the account to refresh the free quota. It is available in the browser for Android. Select the branch with the latest changes. Always enter tasks, if necessary numbered, and written very precisely and in detail.

You can build the apk with Github actions: Stay in your fork, on your user account (you won't be able to start it otherwise), on mobile, click the gear icon and then Actions, and on desktop, click Actions directly. Click Workflows, select Android build, and start your chosen branch. After about 5 minutes, your app will be ready! If compilation errors occur, copy the output to Jules. If there are no more than 5 errors, even Gemini 2.5 Pro can usually handle this very reliably. Otherwise, proceed as you would when programming: Jules, then Claude, then Jules again. After that, the app needs to be signed. I use MiXplorer and select the test signature option. The others don't always work as well or as quickly. You can install it now.

### needed updates

Some models no longer work because Google is changing the interfaces (API). You can usually find the current API names at [aistudio.google.com](https://aistudio.google.com), but you might not find them all there.

In some Android versions, the app exhibits surprising errors. If you find one, please fix it.

Free models accessible via an API can be found [here](https://github.com/cheahjs/free-llm-api-resources)




### Video
[First attempt ever is recorded](https://m.youtube.com/watch?v=o095RSFXJuc)

<br/>

#### Note



If you in your Google account identified as under 18, you need an adult account because Google is (unreasonably) denying you the API key.

Preview models will eventually be removed by Google and unfortunately won't be redirected to finished equivalents. If this happens, please change the API in the code.
