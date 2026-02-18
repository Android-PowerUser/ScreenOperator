@echo off
set "JAVAP=C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot\bin\javap.exe"
set "JAR=C:\Users\Android PowerUser\.gradle\caches\transforms-3\308c516c7eca9e51bcc90ecd445e4c41\transformed\tasks-genai-0.10.32-api.jar"
set "JAREXE=C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot\bin\jar.exe"

echo --- JAR CONTENTS (LlmInference) ---
"%JAREXE%" tf "%JAR%" | findstr /i LlmInference

echo.
echo --- JAVAP LlmInference ---
"%JAVAP%" -p -cp "%JAR%" com.google.mediapipe.tasks.genai.llminference.LlmInference

echo.
echo --- JAVAP LlmInference$LlmInferenceOptions ---
"%JAVAP%" -p -cp "%JAR%" com.google.mediapipe.tasks.genai.llminference.LlmInference$LlmInferenceOptions

echo.
echo --- JAVAP LlmInference$LlmInferenceOptions$Builder ---
"%JAVAP%" -p -cp "%JAR%" com.google.mediapipe.tasks.genai.llminference.LlmInference$LlmInferenceOptions$Builder
