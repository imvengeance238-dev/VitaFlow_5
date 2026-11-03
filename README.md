# Vita Flow

Open in Android Studio (File > Open), sync, run. Package `app.vitaflow.com`, minSdk 31.

Flow: login or sign up -> profile -> roadmap -> app. Tabs include Home, Fitness, Tasks, Max chat, and Profile/Settings.

Max uses the Google Gemini API (via its OpenAI-compatible endpoint). Get a key from https://aistudio.google.com/apikey and add it in **Settings > Max AI**, then use **সংযোগ পরীক্ষা** to verify the connection. The model name (default `gemini-flash-latest`) can be changed in the same section. The key is encrypted at rest with Android Keystore. A key embedded in a private APK is not fully protected from extraction.

Optional speech packs can be added in **Settings > Max AI**. The on-device Gemma/LiteRT-LM chat model has been removed.
