# <img width="48" height="48" alt="logo" src="https://github.com/user-attachments/assets/dd7449ba-2876-4b4e-9a7b-d6ff35fe54d7" /><?xml version="1.0" encoding="UTF-8"?> Sync To Android Data

An Android utility app to allow file synchronization within the `Android/data` directory.

---

## 🖼️ Background
Since Android 11, the `Android/data` directory has been locked down by the new scoped storage paradigm. Standard file explorers and sync solutions (like OneDrive, Syncthing, or Dropbox) can no longer access these folders. This makes it nearly impossible to automatically sync game saves or app data/configuration to the cloud/self-hosted servers for apps that store data in this directory.

**Sync To Android Data** leverages [Shizuku](https://github.com/rikkaapps/shizuku) to automate file transfer to and from locations in `Android/data`, enabling data for these previously-restricted apps to be synchronized. Defined "Sync Entries" automatically move files between these restricted internal folders and a standard external folder of your choice when the "target app" is opened or closed.

## 📦 Installation
1.  Go to the [**Releases**](https://github.com/kamren-zirger/sync-to-android-data/releases) page of this repository.
2.  Download the latest `sync-to-android-data.apk`.
3.  Install the APK on your Android device.
4.  Use the built-in wizard to setup the app, including installation of Shizuku and all other prerequisites.

## ⚙️ How it Works

* **Sync Entries** contain configuration for each app you intend to keep data in sync for.
  * <img width="302" height="81" alt="Screenshot 2026-08-11 at 11 11 22 PM" src="https://github.com/user-attachments/assets/508b572d-6ac4-4dec-a675-3ff282466388" />
* **Target Apps** within Sync Entries define which package will be monitored for open and close by the sync engine (the app you intend to keep data in sync for).
  * <img width="310" height="106" alt="Screenshot 2026-08-11 at 11 11 35 PM" src="https://github.com/user-attachments/assets/2bc32ec0-5346-4b24-9558-08c9cbbd54f8" />
* **Directory Pairs** within Sync Entries define which directories will be kept in sync based on the targeted application. Multiple Directory Pairs can be created for each Sync Entry.
  * **External Directories** within Directory Pairs define the location to be used as a source when the Target App is opened (External -> Internal sync occurs). Typically, these are directories that are accessible to your cloud storage/sync application.
  * **Internal Directories** within Directory Pairs define the location to be used as a source when the Target App is closed (Internal -> External sync occurs). Typically, these are directories within the Target App's respective folder in `Android/data`.
  * <img width="306" height="318" alt="Screenshot 2026-08-11 at 11 11 46 PM" src="https://github.com/user-attachments/assets/4f989e18-921c-4fa0-b43d-4afdc446b03e" />

## 🎥 Tutorial/Demonstration Video

[Screen_recording_20260811_230750.webm](https://github.com/user-attachments/assets/75de983b-d6c2-4a65-ad0a-c46a1265e132)

## ⚠️ Safety & Disclaimer

* **Backup Recommended**: Always keep a primary backup of your important data.
* **Data Loss Risk**: Misconfiguration (e.g., swapping paths), failure of a dependency like Shizuku, or unhandled errors could lead to unintended results.
* **Disclaimer**: This software is provided "as is," and the developer is not responsible for any data loss incurred while using this app.

---

## ⚖️ License
This project is licensed under the [MIT License](LICENSE).
