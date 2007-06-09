/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;

public class VFileCreateEvent extends VFileEvent {
  private VirtualFile myParent;
  private final boolean myDirectory;
  private String myChildName;

  public VFileCreateEvent(final Object requestor, final VirtualFile parent, final String childName, boolean isDirectory, boolean isFromRefresh) {
    super(requestor, isFromRefresh);
    myChildName = childName;
    myParent = parent;
    myDirectory = isDirectory;
  }

  public String getChildName() {
    return myChildName;
  }

  public boolean isDirectory() {
    return myDirectory;
  }

  public VirtualFile getParent() {
    return myParent;
  }

  @NonNls
  public String toString() {
    return "VfsEvent[create " + (isDirectory() ? "dir " : "file ") + myChildName +  " in " + myParent.getUrl() + "]";
  }

  public String getPath() {
    return myParent.getPath() + "/" + myChildName;
  }

  public VirtualFileSystem getFileSystem() {
    return myParent.getFileSystem();
  }

  public boolean isValid() {
    return myParent.findChild(myChildName) == null;
  }
}