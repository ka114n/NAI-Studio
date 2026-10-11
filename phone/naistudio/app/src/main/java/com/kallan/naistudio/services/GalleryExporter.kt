package com.kallan.naistudio.services

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

object GalleryExporter {
    fun export(context: Context, files: List<File>, tree: Uri): GalleryExportBatch.Result {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return GalleryExportBatch.copy(files) { source ->
            val uri = DocumentsContract.createDocument(resolver, parent, "image/png", source.name)
            uri?.let {
                GalleryExportBatch.Target(
                    open = { resolver.openOutputStream(uri, "wt") },
                    delete = { DocumentsContract.deleteDocument(resolver, uri); Unit },
                )
            }
        }
    }
}
