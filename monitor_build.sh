#!/bin/bash
TOPIC="Jules"
PROGRESS=0
while true; do
    PANE_CONTENT=$(tmux capture-pane -t default -p)
    if echo "$PANE_CONTENT" | grep -q "Build and sign process complete."; then
        curl -d "Fortschritt: 100% - Build & Sign abgeschlossen!" ntfy.sh/$TOPIC
        break
    elif echo "$PANE_CONTENT" | grep -q "Signing the APK..."; then
        PROGRESS=90
    elif echo "$PANE_CONTENT" | grep -q "Generating test signing key..."; then
        PROGRESS=85
    elif echo "$PANE_CONTENT" | grep -q "Building the application..."; then
        # Within gradle, we can only guess without parsing gradle's own progress
        PROGRESS=40
    elif echo "$PANE_CONTENT" | grep -q "Installing SDK packages..."; then
        PROGRESS=20
    elif echo "$PANE_CONTENT" | grep -q "Starting build and sign process."; then
        PROGRESS=5
    fi
    curl -d "Fortschritt: $PROGRESS% (geschätzt) - Task läuft im Hintergrund." ntfy.sh/$TOPIC
    sleep 60
done
