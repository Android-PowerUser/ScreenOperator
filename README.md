## Screen Operator
### Operates the screen with AI
This Android app operates the screen with commands from vision LLMs.


### OpenClaw for Android
It can now execute commands directly in Termux via API. After each update, click "Restore System Message" to access the new tools.


<img src="https://github.com/Android-PowerUser/ScreenOperator/blob/main/Screenshot_20260603-084407_Screen%20Operator.png" alt="" width="141"/> <img src="https://github.com/Android-PowerUser/ScreenOperator/blob/main/Screenshot_20250802-231135_Screen%20Operator.png" alt="" width="141"/>


## Download and install

Due to a GitHub restriction, you must be logged in to view the nightly download links:
</br>

[nightly builds](https://github.com/Android-PowerUser/ScreenOperator/actions) from Github actions (You probably must reinstall the app because of different signatures) or install the </br>
[Screen Operator v3.apk](https://github.com/Android-PowerUser/ScreenOperator/releases/download/v2026.06.02/Screen.Operator.v3.apk) (without log in)

Updates in Github are much faster than on the Play Store and have no restrictions.
</br>

![](https://img.shields.io/github/downloads/Android-PowerUser/ScreenOperator/latest/total.svg?label=Screen%20Operator%20latest%20release%20Downloads&v=70) ![](https://img.shields.io/github/downloads/Android-PowerUser/ScreenOperator/total.svg?label=Screen%20Operator%20Downloads&v=69) from Github (without nightly builds)


</br> 


## Develop Screen Operator with AI


This app can be developed entirely by AI and therefore its development can be continued by anyone:

To vibe coding for free I use [Claude.ai](https://claude.ai). You can connect it directly to GitHub, but only over the Website. You will have to fork the project first so that you can edit it. It has a free quota that will be every 5 hours refreshed and you can also easy switch the account to refresh the free quota. It's best to use different browsers for this. Use a GitHub access token and Claude can automatically push the changes. Sonnet 5 sometimes refuses to work with the token. In this case, use Sonnet 4.6. Add the token and perhaps the link to the repo/branch (code) to your preferences in Claude. Then you don't have to enter the same information every time. Select the branch with the latest changes. Always enter tasks, if necessary numbered, and written very precisely and in detail.

You can build the apk with Github actions: Stay in your fork, on your user account (you won't be able to start it otherwise), on mobile, click the gear icon and then Actions, and on desktop, click Actions directly. Click Workflows, select Android build, and start your chosen branch. After about 5 minutes, your app will be ready!

<br/>

### Share your database from Screen Operator

[Here](https://github.com/Android-PowerUser/ScreenOperator/discussions/87) you can share your own database

<br/>
#### • The first Android agent app in the world (since June/2025)


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
