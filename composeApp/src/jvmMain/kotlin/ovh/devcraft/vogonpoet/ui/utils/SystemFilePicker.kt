package ovh.devcraft.vogonpoet.ui.utils

import org.lwjgl.util.tinyfd.TinyFileDialogs

object SystemFilePicker {
    fun selectFolder(
        title: String,
        defaultPath: String? = null,
    ): String? {
        // tinyfd_selectFolderDialog returns null if canceled
        return TinyFileDialogs.tinyfd_selectFolderDialog(title, defaultPath ?: System.getProperty("user.home"))
    }
}
