package com.salutem.order;
import java.net.URI;
import org.eclipse.core.resources.IFile;
import org.eclipse.ui.*;

/** Native file inputs do not have to advertise IFile through getAdapter(). */
public final class SubreportInputs {
 private SubreportInputs(){}
 public static URI localUri(IEditorInput input){
  if(input==null)return null;
  IFile file=input instanceof IFileEditorInput?((IFileEditorInput)input).getFile():input.getAdapter(IFile.class);
  URI uri=file==null?null:file.getLocationURI();
  if(uri==null && file!=null)uri=file.getRawLocationURI();
  if(uri==null && input instanceof IURIEditorInput)uri=((IURIEditorInput)input).getURI();
  return uri!=null && "file".equalsIgnoreCase(uri.getScheme())?uri:null;
 }
}
