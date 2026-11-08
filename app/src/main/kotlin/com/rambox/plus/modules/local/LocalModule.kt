// a module added from a zip with no service behind it: a look, a player, a font, pages, nothing that ever reaches the network

package com.rambox.plus.modules.local

import com.rambox.plus.module.AccountState
import com.rambox.plus.module.LookModule
import com.rambox.plus.module.ModuleHost
import com.rambox.plus.module.ModuleInfo
import com.rambox.plus.module.NO_ACCOUNT_LABEL
import com.rambox.plus.modules.skin.ModuleSkin
import com.rambox.plus.modules.skin.SkinParser
import com.rambox.plus.modules.skin.SkinSource
import com.rambox.plus.modules.skin.appliedLook
import com.rambox.plus.modules.web.WebModuleManifest
import java.io.File

internal class LocalModule(manifest: WebModuleManifest, files: File?) : LookModule, SkinSource {

    override val info = ModuleInfo(
        id = idFor(manifest),
        name = manifest.name.ifBlank { manifest.id },
        version = manifest.version.ifBlank { "1" },
        description = manifest.description.ifBlank { "Added from a file" },
        author = manifest.author,
    )

    override val skin: ModuleSkin? = SkinParser.parse(info.id, info.name, manifest.player, manifest.font, manifest.pages, files, base = null)

    override val look: Map<String, String> = appliedLook(manifest.lookValues, skin)

    override fun attach(host: ModuleHost) = Unit

    // there's no server to ask, it lives on this phone
    override suspend fun verifyAccount(): AccountState = AccountState.SignedIn(NO_ACCOUNT_LABEL)

    companion object {
        // local. in front, so a file can never take the place of a built-in module or one added from a link
        fun idFor(manifest: WebModuleManifest): String =
            "local." + manifest.id.ifBlank { manifest.name }.lowercase().replace(Regex("[^a-z0-9._-]"), "-")
    }
}
