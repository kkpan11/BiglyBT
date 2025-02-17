/*
 * Created on Dec 19, 2006
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */


package com.biglybt.core.content;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.biglybt.core.tag.*;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadAttributeListener;
import com.biglybt.pif.download.DownloadManager;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;

/**
 * Used in UPnP for something
 *
 */
public class
PlatformContentDirectory
	implements ContentDirectory
{
	private static boolean registered = false;

	private static TorrentAttribute	ta_category;
	private final DownloadManager downloadManager;

	public static synchronized void
	register()
	{
		if ( !registered ){

			registered = true;

			ta_category = PluginInitializer.getDefaultInterface().getTorrentManager().getAttribute( TorrentAttribute.TA_CATEGORY );

			ContentDirectoryManager.registerDirectory( new PlatformContentDirectory());
		}
	}

	private static CopyOnWriteList<ContentDirectoryListener>	listeners = new CopyOnWriteList<>();

	public PlatformContentDirectory() {
		downloadManager = PluginInitializer.getDefaultInterface().getDownloadManager();
	}

	@Override
	public Content
	lookupContent(
		Map		attributes )
	{
		return null;
	}

	@Override
	public ContentDownload
	lookupContentDownload(
		Map 		attributes )
	{
		byte[]	hash = (byte[])attributes.get( AT_BTIH );

		try{
			final Download download = downloadManager.getDownload(hash);

			if ( download == null ){

				return( null );
			}

			return(
				new ContentDownload()
				{
					@Override
					public Download
					getDownload()
					{
						return( download );
					}

					@Override
					public Object
					getProperty(
						String		name )
					{
						return( null );
					}
				});

		}catch( Throwable e ){

			return( null );
		}
	}

	
	@Override
	public ContentFile
	lookupContentFile(
		Map 		attributes)
	{
		byte[]	hash 	= (byte[])attributes.get( AT_BTIH );
		int		index	= ((Integer)attributes.get( AT_FILE_INDEX )).intValue();

		try{

			Download download = downloadManager.getDownload(hash);

			if ( download == null ){

				return( null );
			}
			
			String key = "PlatformContentDirectory:cfhandler";
			
			ContentFileHandler cfh = (ContentFileHandler)download.getUserData( key );
			
			if ( cfh == null ){
				
				cfh = new ContentFileHandler( download );
				
				download.setUserData( key, cfh );
			}
			
			return( cfh.getContentFile( index ));
			
		}catch( Throwable e ){
			
			return( null );
		}
	}
	
	private static class
	ContentFileHandler
	{
		private final Download	download;
		
		private final Map<Integer, ContentFile>	cfs = new ConcurrentHashMap<>();	                                       	                     
		
		private
		ContentFileHandler(
			Download		_download )
		{
			download = _download;
						
			download.addAttributeListener(
					new DownloadAttributeListener()
					{
						@Override
						public void
						attributeEventOccurred(
							Download 			download,
							TorrentAttribute 	attribute,
							int 				eventType )
						{
							fireCatsChanged();
						}
					},
					ta_category,
					DownloadAttributeListener.WRITTEN );

			TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL ).addTagListener(
					PluginCoreUtils.unwrap( download ),
					new TagListener()
					{
						@Override
						public void
						taggableSync(
							Tag tag)
						{
						}

						@Override
						public void
						taggableRemoved(
							Tag 		tag,
							Taggable 	tagged )
						{
							update( tagged );
						}

						@Override
						public void
						taggableAdded(
							Tag 		tag,
							Taggable 	tagged )
						{
							update( tagged );
						}

						private void
						update(
							Taggable	tagged )
						{
							fireTagsChanged();
						}
					});
		}
		
		private	ContentFile
		getContentFile(
			int		index )
		{
			ContentFile acf = cfs.get( index );
			
			if ( acf != null ){
				
				return( acf );
			}
			
			acf = 
				new ContentFile() 
				{
					@Override
					public DiskManagerFileInfo 
					getFile() 
					{
						return( download.getDiskManagerFileInfo(index));
					}
	
					@Override
					public Object 
					getProperty(
						String name )
					{
						DiskManagerFileInfo file = getFile();
						
						try {
							if (name.equals(PT_DATE)) {
	
								return (new Long(file.getDownload().getCreationTime()));
	
							} else if (name.equals(PT_CATEGORIES)) {
	
								try {
									String cat = file.getDownload().getCategoryName();
	
									if (cat != null && cat.length() > 0) {
	
										if (!cat.equalsIgnoreCase("Categories.uncategorized")) {
	
											return (new String[] {
												cat
											});
										}
									}
								} catch (Throwable e) {
	
								}
	
								return (new String[0]);
	
							} else if (name.equals(PT_TAGS)) {
	
								List<Tag> tags = TagManagerFactory.getTagManager().getTagsForTaggable(
										PluginCoreUtils.unwrap(file.getDownload()));
	
									// can be duplicates...
								
								Set<String> tag_names = new HashSet<>();
	
								for (Tag tag : tags) {
	
									if (tag.getTagType().getTagType() == TagType.TT_DOWNLOAD_MANUAL) {
	
										tag_names.add(tag.getTagName(true));
									}
								}
	
								return (tag_names.toArray(new String[tag_names.size()]));
	
							} else if (name.equals(PT_PERCENT_DONE)) {
	
								long size = file.getLength();
	
								return (new Long( size == 0 ? 100 : (1000 * file.getDownloaded() / size)));
	
							} else if (name.equals(PT_ETA)) {
	
								return(getETA(file));
							}
						} catch (Throwable e) {
						}
	
						return (null);
					}
				};

			cfs.put( index, acf );		

			return( acf );
		}
		
		private void
		fireCatsChanged()
		{
			for ( ContentDirectoryListener l: listeners ){

				for ( ContentFile cf: cfs.values()){
					
					l.contentChanged( cf, ContentFile.PT_CATEGORIES );
				}
			}
		}

		private void
		fireTagsChanged()
		{
			for ( ContentDirectoryListener l: listeners ){

				for ( ContentFile cf: cfs.values()){
				
					l.contentChanged( cf, ContentFile.PT_TAGS );
				}
			}
		}
	}
	


	private static long
	getETA(
		DiskManagerFileInfo		file )
	{
		try{
			if ( file.getDownloaded() == file.getLength()){

				return( 0 );
			}

			if ( file.isDeleted() || file.isSkipped()){

				return( Long.MAX_VALUE );
			}

			long eta = file.getDownload().getStats().getETASecs();

			if ( eta < 0 ){

				return( Long.MAX_VALUE );
			}

			return( eta );

		}catch( Throwable e ){

			return( Long.MAX_VALUE );
		}
	}

	public static void
	fireCatsChanged(
		ContentFile		cf )
	{
		for ( ContentDirectoryListener l: listeners ){
			
			l.contentChanged( cf, ContentFile.PT_CATEGORIES );
		}
	}

	public static void
	fireTagsChanged(
		ContentFile		cf )
	{
		for ( ContentDirectoryListener l: listeners ){
			
			l.contentChanged( cf, ContentFile.PT_TAGS );
		}
	}

	
	@Override
	public void
	addListener(
		ContentDirectoryListener listener )
	{
		listeners.add( listener );
	}

	@Override
	public void
	removeListener(
		ContentDirectoryListener listener )
	{
		listeners.remove( listener );
	}

}
