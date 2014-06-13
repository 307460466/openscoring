/*
 * Copyright (c) 2014 Villu Ruusmann
 *
 * This file is part of Openscoring
 *
 * Openscoring is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Openscoring is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Openscoring.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.openscoring.server;

import java.io.*;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;

import org.openscoring.service.*;

import org.dmg.pmml.*;

public class DirectoryDeployer extends Thread {

	private ModelRegistry modelRegistry = null;

	private Path directory = null;


	public DirectoryDeployer(ModelRegistry modelRegistry, Path directory){
		setModelRegistry(modelRegistry);
		setDirectory(directory);
	}

	@Override
	public void run(){

		try {
			deploy();
		} catch(Exception e){
			// Ignored
		}
	}

	private void deploy() throws IOException {
		Path directory = getDirectory();

		DirectoryStream<Path> children = Files.newDirectoryStream(directory);
		for(Path child : children){
			process(StandardWatchEventKinds.ENTRY_CREATE, child);
		}

		FileSystem fileSystem = directory.getFileSystem();

		WatchService watcher = fileSystem.newWatchService();

		try {
			WatchKey key = directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
			if(key.isValid()){
				process(key);
			}

			while(key.reset()){

				try {
					key = watcher.take();
				} catch(InterruptedException ie){
					break;
				}

				if(key.isValid()){
					process(key);
				}
			}
		} finally {
			watcher.close();
		}
	}

	private void process(WatchKey key){
		Path directory = getDirectory();

		List<WatchEvent<?>> events = key.pollEvents();
		for(WatchEvent<?> event : events){
			process((WatchEvent.Kind<Path>)event.kind(), directory.resolve((Path)event.context()));
		}
	}

	private void process(WatchEvent.Kind<Path> kind, Path path){
		ModelRegistry modelRegistry = getModelRegistry();

		String id = (path.getFileName()).toString();

		// Remove file name extension
		// Start the search from the beginning of the file name, in case there are multiple extensions
		int dot = id.indexOf('.');
		if(dot > -1){
			id = id.substring(0, dot);
		} // End if

		if((StandardWatchEventKinds.ENTRY_CREATE).equals(kind)){

			try {
				PMML pmml;

				InputStream is = Files.newInputStream(path);

				try {
					pmml = ModelRegistry.unmarshal(is);
				} finally {
					is.close();
				}

				modelRegistry.put(id, pmml);
			} catch(Exception e){
				// Ignored
			}
		} else

		if((StandardWatchEventKinds.ENTRY_DELETE).equals(kind)){
			modelRegistry.remove(id);
		}
	}

	public ModelRegistry getModelRegistry(){
		return this.modelRegistry;
	}

	private void setModelRegistry(ModelRegistry modelRegistry){
		this.modelRegistry = modelRegistry;
	}

	public Path getDirectory(){
		return this.directory;
	}

	private void setDirectory(Path directory){
		this.directory = directory;
	}
}