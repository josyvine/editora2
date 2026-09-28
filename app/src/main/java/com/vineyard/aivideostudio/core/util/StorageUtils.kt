package com.vineyard.aivideostudio.core.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.vineyard.aivideostudio.core.result.AppError
import com.vineyard.aivideostudio.core.result.AppResult
import java.io.File
import java.io.FileInputStream

object StorageUtils {

    fun getAvailableStorageBytes(context: Context): Long {
        return try {
            val path: File = context.filesDir
            val stat = StatFs(path.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            -1L
        }
    }

    fun hasSufficientSpace(context: Context, requiredBytes: Long): Boolean {
        val available = getAvailableStorageBytes(context)
        return available == -1L || available > requiredBytes
    }

    /**
     * Saves a video file into the public device storage (Movies/Editora) using MediaStore.
     * Instantly visible in Gallery, Photos, and Media Players.
     *
     * @param context Application context
     * @param sourceFile The internal sandboxed video file to export
     * @param displayName Desired name for the video file
     */
    fun saveVideoToGallery(
        context: Context,
        sourceFile: File,
        displayName: String = "Editora_${System.currentTimeMillis()}.mp4"
    ): AppResult<Uri> {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return AppResult.Error(AppError.StorageError("Source video file does not exist or is empty."))
        }

        return try {
            val cleanName = if (displayName.endsWith(".mp4", ignoreCase = true)) displayName else "$displayName.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, cleanName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Editora")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val contentResolver = context.contentResolver
            val uri = contentResolver.insert(collection, values)
                ?: return AppResult.Error(AppError.StorageError("Failed to create MediaStore entry for video export."))

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.flush()
            } ?: return AppResult.Error(AppError.StorageError("Failed to open output stream to MediaStore URI."))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }

            AppResult.Success(uri)
        } catch (e: Exception) {
            AppResult.Error(AppError.StorageError("Failed saving video to gallery: ${e.message}"))
        }
    }

    /**
     * Saves a video file into a custom directory selected via Storage Access Framework (SAF Tree Uri).
     */
    fun saveVideoToTreeUri(
        context: Context,
        sourceFile: File,
        treeUri: Uri,
        displayName: String = "Editora_${System.currentTimeMillis()}.mp4"
    ): AppResult<Uri> {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return AppResult.Error(AppError.StorageError("Source video file does not exist or is empty."))
        }

        return try {
            val cleanName = if (displayName.endsWith(".mp4", ignoreCase = true)) displayName else "$displayName.mp4"
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

            val targetUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                "video/mp4",
                cleanName
            ) ?: return AppResult.Error(AppError.StorageError("Failed creating document in chosen directory."))

            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.flush()
            } ?: return AppResult.Error(AppError.StorageError("Failed writing video to destination folder."))

            AppResult.Success(targetUri)
        } catch (e: Exception) {
            AppResult.Error(AppError.StorageError("Failed exporting video to custom directory: ${e.message}"))
        }
    }

    /**
     * Streams video bytes into a single-file destination URI chosen via system file picker.
     */
    fun copyVideoToUri(
        context: Context,
        sourceFile: File,
        targetUri: Uri
    ): AppResult<Uri> {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return AppResult.Error(AppError.StorageError("Source video file does not exist or is empty."))
        }

        return try {
            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.flush()
            } ?: return AppResult.Error(AppError.StorageError("Failed writing video to destination URI."))

            AppResult.Success(targetUri)
        } catch (e: Exception) {
            AppResult.Error(AppError.StorageError("Failed writing video: ${e.message}"))
        }
    }
}