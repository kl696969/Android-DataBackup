package android.os

import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.FileDescriptor

object MemoryFileHidden {
    fun getFileDescriptor(memoryFile: MemoryFile): FileDescriptor {
        return HiddenApiBypass.invoke(MemoryFile::class.java, memoryFile, "getFileDescriptor") as FileDescriptor
    }
}
