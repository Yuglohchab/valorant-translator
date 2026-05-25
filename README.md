# 🎮 Valorant Mobile Live Translator

An Android application that translates the Chinese Valorant Mobile user interface to English in real-time. It uses an on-screen overlay to display translation results directly on top of the active game window without interfering with the game client.

---

## ✨ Features
- **On-Device OCR & Translation:** Utilizes Google ML Kit's Chinese Text Recognition and Language Translation API. No internet connection or API keys required after initial model download.
- **Real-Time Display:** Scans the active viewport every 900ms and draws non-intrusive floating transparent layers over target interface components.
- **Safe Architecture:** Operates entirely by visually capturing display output using standard Android media projection pipelines. It does not hook into game memory or modify internal execution strings.

---

## 🏗️ System Architecture

The following flow chart details how the components within this application communicate to translate game text on the fly:
