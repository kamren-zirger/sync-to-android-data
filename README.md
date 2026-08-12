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
* **Target Apps** within Sync Entries define which package will be monitored for open and close by the sync engine (the app you intend to keep data in sync for).
* **Directory Pairs** within Sync Entries define which directories will be kept in sync based on the targeted application. Multiple Directory Pairs can be created for each Sync Entry.
  * **External Directories** within Directory Pairs define the location to be used as a source when the Target App is opened (External -> Internal sync occurs). Typically, these are directories that are accessible to your cloud storage/sync application.
  * **Internal Directories** within Directory Pairs define the location to be used as a source when the Target App is closed (Internal -> External sync occurs). Typically, these are directories within the Target App's respective folder in `Android/data`.

## ⚠️ Safety & Disclaimer

* **Backup Recommended**: Always keep a primary backup of your important data.
* **Data Loss Risk**: Misconfiguration (e.g., swapping paths), failure of a dependency like Shizuku, or unhandled errors could lead to unintended results.
* **Disclaimer**: This software is provided "as is," and the developer is not responsible for any data loss incurred while using this app.

---

## ⚖️ License
This project is licensed under the [MIT License](LICENSE).
