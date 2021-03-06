/*
 * This program is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software 
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this 
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html 
 * or from the Free Software Foundation, Inc., 
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright 2005 - 2009 Pentaho Corporation.  All rights reserved.
 *
 *
 * @created Jul 11, 2005 
 * @author James Dixon
 * 
 */

package org.pentaho.platform.web.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.platform.api.engine.IContentOutputHandler;
import org.pentaho.platform.api.engine.IMimeTypeListener;
import org.pentaho.platform.api.engine.IOutputDef;
import org.pentaho.platform.api.engine.IOutputHandler;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.api.engine.IRuntimeContext;
import org.pentaho.platform.api.repository.IContentItem;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.web.http.messages.Messages;

public class HttpOutputHandler implements IOutputHandler, IMimeTypeListener {
  private static final Log logger = LogFactory.getLog(HttpOutputHandler.class);
  
  private HttpServletResponse response;

  protected IContentItem outputContent;

  private IContentItem feedbackContent;

  boolean allowFeedback;

  @Deprecated
  private boolean contentGenerated;

  private IPentahoSession session;

  private IMimeTypeListener mimeTypeListener;

  protected IRuntimeContext runtimeContext;

  private int outputType = IOutputHandler.OUTPUT_TYPE_DEFAULT;
  
  private OutputStream destinationOutputStream;
  
  private boolean responseExpected;

  public HttpOutputHandler(final HttpServletResponse response, final OutputStream outputStream, final boolean allowFeedback) {
    this.response = response;
    destinationOutputStream = outputStream;

    outputContent = new HttpContentItem(destinationOutputStream, this);
    ((HttpContentItem) outputContent).setMimeTypeListener(this);
    feedbackContent = new HttpContentItem(destinationOutputStream, this);

    this.allowFeedback = allowFeedback;
    contentGenerated = false;
  }

  public void setSession(final IPentahoSession session) {
    this.session = session;
  }

  public IPentahoSession getSession() {
    return session;
  }

  public void setOutputPreference(final int outputType) {
    this.outputType = outputType;
  }

  public int getOutputPreference() {
    return outputType;
  }

  public void setMimeType(final String mimeType) {

    if (mimeTypeListener != null) {
      mimeTypeListener.setMimeType(mimeType);
    }
    response.setContentType(mimeType);
  }

  public void setName( String name ) {
	    if (mimeTypeListener != null) {
	        mimeTypeListener.setName(name);
	      }
  }
  
  @Deprecated
  public boolean contentDone() {
    return contentGenerated;
  }
  
  public boolean allowFeedback() {
    return allowFeedback;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.pentaho.core.solution.IOutputHandler#getOutputDefs()
   */
  public Map getOutputDefs() {
    return null;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.pentaho.core.solution.IOutputHandler#getOutputDef(java.lang.String)
   */
  public IOutputDef getOutputDef(final String name) {
    return null;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.pentaho.core.solution.IOutputHandler#getFeedbackStream()
   */
  public IContentItem getFeedbackContentItem() {
    if (allowFeedback) {
      // assume that content is generated becuase of this
      contentGenerated = true;
      /*
       * if someone is requesting a feedbackContentItem, we can assume they tend to write feedback
       * back to the client, so we set the flag here
       */
      responseExpected = true;
      return feedbackContent;
    }
    return null;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.pentaho.core.solution.IOutputHandler#getContentStream()
   */
  public IContentItem getOutputContentItem(final String objectName, final String contentName, final String solution, final String instanceId,
      final String mimeType) {
    if (objectName.equals(IOutputHandler.RESPONSE) && contentName.equals(IOutputHandler.CONTENT)) {
      // assume that content is generated becuase of this
      // change the content type if necessary
      if(mimeType != null) {
        outputContent.setMimeType(mimeType);
      }
      contentGenerated = true;
      /*
       * if someone is requesting an outputContentItem, they are intending to write to the response
       * output stream.
       */
      responseExpected = true;

      return outputContent;
    } else {
      IContentOutputHandler output = null;
   
      // this code allows us to stay backwards compatible
      if ((contentName != null) && (contentName.indexOf(":") == -1)) { //$NON-NLS-1$
        output = PentahoSystem.getOutputDestinationFromContentRef(objectName + ":" + contentName, session); //$NON-NLS-1$
      } else {
        output = PentahoSystem.getOutputDestinationFromContentRef(contentName, session);
        if (output == null) {
          output = PentahoSystem.getOutputDestinationFromContentRef(objectName + ":" + contentName, session); //$NON-NLS-1$
        }
      }
      if (output != null) {
        output.setInstanceId(instanceId);
        output.setMimeType(mimeType);
        output.setSolutionName(solution);
        return output.getFileOutputContentItem();
      }
    }
    return null;
  }

  public IContentItem getOutputContentItem(final String objectName, final String contentName, final String title, final String url,
      final String solution, final String instanceId, final String mimeType) {
    return getOutputContentItem(objectName, contentName, solution, instanceId, mimeType);
  }

  public void setContentItem(final IContentItem content, final String objectName, final String contentName) {
    if (objectName.equals(IOutputHandler.RESPONSE) && contentName.equals(IOutputHandler.CONTENT)) {
      setMimeType(content.getMimeType());
    }
  }

  public void setOutput(final String name, final Object value) {
    if (value == null) {
      HttpOutputHandler.logger.warn(Messages.getString("HttpOutputHandler.WARN_0001_VALUE_IS_NULL")); //$NON-NLS-1$
      return;
    }
    
    /*
     * if setOutput is called, it means someone intends to write some data to the response stream,
     * which they may not have gotten a handle to earlier through getOutputContentItem*. So we need to set the flag here.
     */
    responseExpected = true;

    if ("redirect".equalsIgnoreCase(name)) { //$NON-NLS-1$
      try {
        response.sendRedirect(value.toString());
      } catch (IOException ioe) {
        HttpOutputHandler.logger.error(Messages.getString("HttpOutputHandler.ERROR_0001_REDIRECT_FAILED",value.toString()), ioe); //$NON-NLS-1$
      }
    } else if ("header".equalsIgnoreCase(name)) { //$NON-NLS-1$
      try {
        if (value instanceof Map) {
          for (Iterator it = ((Map) value).entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            response.addHeader(entry.getKey().toString(), entry.getValue().toString());
          }
        } else {
          String strVal = value.toString();
          int i = strVal.indexOf('=');
          String headerName = strVal.substring(0, i);
          String headerValue = strVal.substring(i + 1);
          response.addHeader(headerName, headerValue);
        }
      } catch (IndexOutOfBoundsException e) {
         HttpOutputHandler.logger.error(e.getLocalizedMessage());
      }
    } else if (IOutputHandler.CONTENT.equalsIgnoreCase(name)) {
      try {
        if (value instanceof IContentItem) {
          IContentItem content = (IContentItem) value;
          // See if we should process the input stream. If it's from
          // the content repository, then there's an input stream.
          // SimpleContentItem and HttpContentItem both return null from
          // getInputStream().
          InputStream inStr = content.getInputStream();
          if (inStr != null) {
            if ((response.getContentType() == null)
                || (!response.getContentType().equalsIgnoreCase(content.getMimeType()))) {
              // response.setContentType( content.getMimeType() );
              setMimeType(content.getMimeType());
            }
            try {
              //Bad idea to write to the output stream directly. This prevents us from
              //auditing writes.  Use the managed destinationOutputStream instead.
              //OutputStream outStr = response.getOutputStream();
              int inCnt = 0;
              byte[] buf = new byte[4096];
              while (-1 != (inCnt = inStr.read(buf))) {
                destinationOutputStream.write(buf, 0, inCnt);
              }
            } finally {
              try {
                inStr.close();
              } catch (IOException ignored) {
                
              }
            }
            contentGenerated = true;
          }
        } else {
          if (response.getContentType() == null) {
            setMimeType("text/html"); //$NON-NLS-1$
          }

          destinationOutputStream.write(value.toString().getBytes());
          contentGenerated = true;
        }
      } catch (IOException ioe) {
        HttpOutputHandler.logger.error(null, ioe);
      }
    }
  }

  public IMimeTypeListener getMimeTypeListener() {
    return mimeTypeListener;
  }

  public void setMimeTypeListener(final IMimeTypeListener mimeTypeListener) {
    this.mimeTypeListener = mimeTypeListener;
  }
  
  /**
   * @deprecated use {@link #getOutputContentItem(String, String, String, String, String)}
   */
  public IContentItem getOutputContent() {
    return getOutputContentItem(IOutputHandler.RESPONSE, IOutputHandler.CONTENT, null, null, null);
  }

  public HttpServletResponse getResponse() {
    return response;
  }

  public void setResponse(final HttpServletResponse response) {
    this.response = response;
  }
  
  public boolean isResponseExpected() {
    return responseExpected;
  }

  public void setRuntimeContext(final IRuntimeContext runtimeContext) {
    this.runtimeContext = runtimeContext;
  }

  public boolean isWritable() {
    return false;
  }
}
