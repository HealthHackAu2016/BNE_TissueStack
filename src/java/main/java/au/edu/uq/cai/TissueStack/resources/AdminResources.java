package au.edu.uq.cai.TissueStack.resources;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;

import au.edu.uq.cai.TissueStack.dataobjects.Configuration;
import au.edu.uq.cai.TissueStack.dataobjects.DataSet;
import au.edu.uq.cai.TissueStack.dataobjects.Response;
import au.edu.uq.cai.TissueStack.dataprovider.ConfigurationDataProvider;
import au.edu.uq.cai.TissueStack.dataprovider.DataSetDataProvider;
import au.edu.uq.cai.TissueStack.dataobjects.MincInfo;
import au.edu.uq.cai.TissueStack.jni.TissueStack;
import au.edu.uq.cai.TissueStack.rest.AbstractRestfulMetaInformation;
import au.edu.uq.cai.TissueStack.rest.Description;

/*
 * !!!!!!! IMPORTANT : always call SecurityResources.checkSession(session) to check for session validity !!!!!!
 */
@Path("/admin")
@Description("Tissue Stack Admin Resources")
public final class AdminResources extends AbstractRestfulMetaInformation {
	
	final static Logger logger = Logger.getLogger(AdminResources.class);
	
	private final static String DEFAULT_UPLOAD_DIRECTORY = "/opt/upload";
	private final static String DEFAULT_DATA_DIRECTORY = "/opt/data";
	private final static long DEFAULT_MAX_UPLOAD_SIZE = 1024 * 1024 * 1024;
			
	@Path("/")
	public RestfulResource getDefault() {
		return this.getAdminResourcesMetaInfo();
	}
		
	@Path("/upload")
	@Description("Uploads a file to the upload directory")
	public RestfulResource uploadFile(
			@Description("Internal parameter")
			@Context HttpServletRequest request,
			@Description("Mandatory: The session token")
			@QueryParam("session") String session) {
		// check permissions
		if (!SecurityResources.checkSession(session)) {
			throw new RuntimeException("Invalid Session! Please Log In.");
		}
		
		boolean isMultipart = ServletFileUpload.isMultipartContent(request);
		
		// preliminary check
		if (!isMultipart) {
			throw new RuntimeException("Not a File Upload");
		}

		// query file upload directory
		final File uploadDirectory = this.getUploadDirectory();
		if (!uploadDirectory.exists()) {
			uploadDirectory.mkdir();
		}
		// check if we have write permission
		if (!uploadDirectory.canWrite()) {
			throw new RuntimeException("Cannot Write to Upload Directory");
		}

		// query maximum upload size
		long maxUploadInBytes = DEFAULT_MAX_UPLOAD_SIZE;
		final Configuration maxUpSize = ConfigurationDataProvider.queryConfigurationById("max_upload_size");
		try {
			maxUploadInBytes = Long.parseLong(maxUpSize.getValue());
		} catch(Exception nullOrFailedNumberConversion) {
			// use default
		}
		
		/* a custom progress listener
		ProgressListener progressListener = new ProgressListener(){
		   // TODO: this has to become more outside accessible for front-end progress updates
		   public void update(long pBytesRead, long pContentLength, int pItems) {
		       if (pContentLength == -1) {
		           System.out.println("So far, " + pBytesRead + " bytes have been read.");
		       } else {
		           System.out.println("So far, " + pBytesRead + " of " + pContentLength
		                              + " bytes have been read.");
		       }
		   }
		};*/
		
		// Create a new file upload handler
		ServletFileUpload upload = new ServletFileUpload();
		//upload.setProgressListener(progressListener);
		
		FileItemIterator files;
		InputStream in = null;
		File fileToBeUploaded = null;

		try {
			// 'loop' through results (we only want the first!) 
			files = upload.getItemIterator(request);

			while (files.hasNext()) {
			    FileItemStream file = files.next();
			    
			    if (file == null || file.getName() == null || file.getName().isEmpty()) {
			    	throw new IllegalArgumentException("No File was Selected!");
			    }
			    
			    if (!file.isFormField()) {
			    	OutputStream out = null;
			    	
			    	byte buffer[] = new byte[1024];
			    	long accBytesRead = 0;
			    	int bytesRead = 0;
			    	boolean erroneousUpload = true;
			    	
			    	try {
				       fileToBeUploaded = new File(uploadDirectory, file.getName());

				       if (fileToBeUploaded.exists()) {
				    	   erroneousUpload = false; // otherwise we delete the existing file
		    			   throw new RuntimeException(
		    					   "File " + file.getName() + " Exists Already In The Upload Destination!");
				       }
				       
				       // open streams
				       in = file.openStream();
			    	   out = new FileOutputStream(fileToBeUploaded);
			    	   
			    	   long lastChunkStep = -1;
			    	   
			    	   while ((bytesRead = in.read(buffer)) > 0) {
					       // if we go under 5% free disk space => stop upload
			    		   // check every 10 megs uploaded
			    		   long chunksOf1024 = accBytesRead / (1024 * 1024); 
					       if ((chunksOf1024 != lastChunkStep && chunksOf1024 % 10 == 0) 
					    		   && uploadDirectory.getUsableSpace() < uploadDirectory.getTotalSpace() * 0.03) {
					    		   throw new RuntimeException("Available disk space is less than 3% of the overall disk space! We abort the upload!");   
				    	   }
					       lastChunkStep = chunksOf1024;

			    		   if (accBytesRead > maxUploadInBytes) {
			    			   throw new RuntimeException(
			    					   "Exceeded number of bytes that can be uploaded. Present limit is: " + maxUploadInBytes);
			    		   }
			    		   out.write(buffer, 0, bytesRead);
			    		   accBytesRead += bytesRead;
			    	   }
			    	   
			    	   // flag upload as clean
			    	   erroneousUpload = false;
					} catch (RuntimeException internallyThrown) {
						// propagate
						throw internallyThrown;
			    	} catch(Exception writeToDiskException) {
			    	  throw new RuntimeException("Failed to Save Uploaded File", writeToDiskException);
			    	} finally {
			    		// partial uploads are deleted
			    		if (erroneousUpload) {
			    			try {
			    				fileToBeUploaded.delete();
			    			} catch (Exception ignored) {
			    				// we can safely ignore that
							}
			    		}
			    		
			    		// clean up
			    		try {
			    			out.close();
			    		} catch(Exception ignored) {
			    			//ignored
			    		}
			    	}
			    }
			    // only one file is supposed to come along
			    break;
			}
		} catch (RuntimeException internallyThrown) {
			// log and propagate
			logger.error("Failed to upload file", internallyThrown);
			throw internallyThrown;
		} catch (Exception e) {
			logger.error("Failed to upload file", e);
			throw new RuntimeException("Failed to upload file", e);
		} finally {
    		try {
	    		// clean up
    			in.close();
    		} catch(Exception ignored) {
    			// ignored
    		}
		}
			
		return new RestfulResource(new Response("Upload finished successfully."));
	}
	
	@Path("/upload_directory")
	@Description("Displays contents of upload directory")
	public RestfulResource readFile() {
		final File fileDirectory = this.getUploadDirectory();
		
		File[] listOfFiles = fileDirectory.listFiles(new FilenameFilter() {
			public boolean accept(File path, String fileOrDir) {
				final File full = new File(path, fileOrDir);
				if (full.isDirectory()) {
					return false;
				}
				return true;
			}
		});
		String fileNames[] = new String[listOfFiles.length];
		int i = 0;
		while (i<fileNames.length) {
			fileNames[i] = listOfFiles[i].getName();
			i++;
		}

 		return new RestfulResource(new Response(fileNames));
	}
	
	@Path("/add_dataset")
	@Description("add uploaded dataset to the configuration database")
	public RestfulResource updateDataSet(
			@Description("Mandatory: a user session token")
			@QueryParam("session") String session,
			@Description("Mandatory: the file name of the already uploaded file")
			@QueryParam("filename") String filename,
			@Description("Optional: A description for the data set that is about to be added")
			@QueryParam("description") String description) {
		// check user session
		if (!SecurityResources.checkSession(session)) {
			throw new RuntimeException("Invalid Session! Please Log In.");
		}
		
		// check for empty filename
		if (filename == null || filename.trim().isEmpty()) {
			throw new IllegalArgumentException("File Parameter Is Empty");
		}
		// check for existence of file
		final File uploadedFile = new File(this.getUploadDirectory(), filename); 
		if (!uploadedFile.exists()) {
			throw new IllegalArgumentException("File '" + uploadedFile.getAbsolutePath() + "' does Not Exist");
		}
		
		// call native code to read meta info from minc file
		final MincInfo newDataSet = new TissueStack().getMincInfo(uploadedFile.getAbsolutePath());
		if (newDataSet == null) {
			throw new RuntimeException("File '" + uploadedFile.getAbsolutePath() + "' could not be processed by native minc info libs. Check whether it is a minc file...");
		}
		// convert minc file info into data set to be stored in the db later on
		final DataSet dataSetToBeAdded = DataSet.fromMincInfo(newDataSet);
		if (dataSetToBeAdded == null) {
			throw new RuntimeException("Failed to convert minc file '" + uploadedFile.getAbsolutePath() + "' into a data set...");
		}
		// set custom description if it exists
		if (description != null && !description.trim().isEmpty()) {
			dataSetToBeAdded.setDescription(description);
		}

		// relocate file from upload directory to data directory
		final File dataDir = this.getDataDirectory();
		if (!dataDir.exists()) { // try to create it if it doesn't exist
			dataDir.mkdirs();
		}
		final File destination = new File(dataDir, uploadedFile.getName());
		if (!uploadedFile.renameTo(destination)) {
			throw new RuntimeException(
					"Failed to move uploaded file (" + uploadedFile.getAbsolutePath()
					+ ") to destination: " + destination.getAbsolutePath());
		}
		dataSetToBeAdded.setFilename(destination.getAbsolutePath());
		
		// store dataSetToBeAdded in the database
		try {
			DataSetDataProvider.insertNewDataSets(dataSetToBeAdded);
		} catch(RuntimeException any) {
			// move file back to upload directory
			destination.renameTo(uploadedFile);
			// propagate error
			throw any;
		}

		// return the new data set
		return new RestfulResource(new Response(dataSetToBeAdded));
	}
		
	private File getUploadDirectory() {
		final Configuration upDir = ConfigurationDataProvider.queryConfigurationById("upload_directory");
		return new File(upDir == null || upDir.getValue() == null ? DEFAULT_UPLOAD_DIRECTORY : upDir.getValue());
	}

	private File getDataDirectory() {
		final Configuration dataDir = ConfigurationDataProvider.queryConfigurationById("data_directory");
		return new File(dataDir == null || dataDir.getValue() == null ? DEFAULT_DATA_DIRECTORY : dataDir.getValue());
	}

	@Path("/meta-info")
	@Description("Shows the Tissue Stack Admin's Meta Info.")
	public RestfulResource getAdminResourcesMetaInfo() {
		return new RestfulResource(new Response(this.getMetaInfo()));
	}
}
