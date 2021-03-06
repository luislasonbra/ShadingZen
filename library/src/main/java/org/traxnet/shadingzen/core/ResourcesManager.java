package org.traxnet.shadingzen.core;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.zip.ZipFile;

/** 
 * The ResourceManager is singleton object that tracks resources and references from those using those resources. 
 * It manages when to unload/load resources from OpenGL memory
 * 
 */
public class ResourcesManager {
	HashMap<String, Resource> _resourcesCache;
    Object [] _cachedResources; // To avoid iterators
	Context _context = null;
	Object _lock = null;
	boolean _dataIsPaused = false;
	SoundPool _soundPool;
	int _autoGeneratedResId = 0;
    String _expansionPackPath = null;
    ZipFile _expansionPack = null;
    int _mimapLevel = 0;


    public void setDefaultMipmapLevel(int level){
        _mimapLevel = level;
    }

    public int getDefaultMimapLevel(){ return _mimapLevel; }
	
	private static ResourcesManager _global_instance = null;
	
	public static ResourcesManager getSharedInstance(){	
		return _global_instance;
	}
	
	static public void setSharedInstance(ResourcesManager manager) throws IllegalAccessException{
		//if(null != _global_instance) throw new IllegalAccessException();
		
		_global_instance = manager;
	}
	
	public SoundPool getSoundPool(){
		return _soundPool;
	}
	
	public ResourcesManager(){
		_resourcesCache = new HashMap<String, Resource>();
		_lock = new Object();
		_soundPool = new SoundPool(8, AudioManager.STREAM_MUSIC, 100);
	}



    public void setExpansionPack(String path) throws Exception {
        _expansionPackPath = path;

        _expansionPack = new ZipFile(_expansionPackPath);

    }
	
	public void setContext(Context context){
		_context = context;
	}
	
	Resource readFromCache(String id){
		Resource res = _resourcesCache.get(id);
		return res;
	}
	
	void writeToCache(String id, Resource res){
		_resourcesCache.put(id, res);
        _cachedResources = _resourcesCache.values().toArray();
	}
	
	void printException(Exception e){
		Log.e("ShadingZen", "Error loading resource:" + e.getLocalizedMessage());
		StringWriter sw = new StringWriter();
	    PrintWriter pw = new PrintWriter(sw);
	    e.printStackTrace(pw);
		Log.e("ShadingZen", sw.toString());
	}
	
	/**
	 * Returns a new resource and add a new reference to the given Entity.
	 * 
	 * @param proto Type of resource to create
	 * @param owner Owner reference this new resource
	 * @return a new instance of the given resource type
	 */
	public Resource factory(Class<? extends Resource> proto, Entity owner){
		return factory(proto, owner, null, -1, null);
	}
	
	/**
	 * Returns a new resource and add a new reference to the given Entity.
	 * 
	 * @param proto Type of resource to create
	 * @param owner Owner reference this new resource
	 * @param id An ID to identify this resource. If asked for a new resource with the same ID, no copy is generate but this one is reused.
	 * @return a new instance of the given resource type
	 */
	public Resource factory(Class<? extends Resource> proto, Entity owner, String id){
		return factory(proto, owner, id, -1, null);
	}
	
	/**
	 * Returns a new resource and add a new reference to the given Entity.
	 * 
	 * @param proto Type of resource to create
	 * @param owner Owner reference this new resource
	 * @param id An ID to identify this resource. If asked for a new resource with the same ID, no copy is generate but this one is reused.
	 * @param res_id The raw resource id to be used during resource loading
	 * @return a new instance of the given resource type
	 */
	public Resource factory(Class<? extends Resource> proto, Entity owner, String id, int res_id){
		return factory(proto, owner, id, res_id, null);
	}
	
	/**
	 * Returns a new resource and add a new reference to the given Entity.
	 * 
	 * @param proto Type of resource to create
	 * @param owner Owner reference this new resource
	 * @param id An ID to identify this resource. If asked for a new resource with the same ID, no copy is generate but this one is reused.
	 * @param res_id The raw resource id to be used during resource loading
	 * @param data An opaque object to be passed during resource creation. It contains initialization data (for example texture parameters).
	 * @return a new instance of the given resource type
	 */
	public Resource factory(Class<? extends Resource> proto, Entity owner, String id, int res_id, Object data){
		synchronized(_lock){
			if(null == id){
				id = generateResourceId(res_id);
			}
			// Check if already loaded
			Resource res = readFromCache(id);
            if (checkAndAddReferenceToOwner(owner, res)) return res;

            // If not loaded, read from storage
			try {
				res = (Resource)proto.newInstance();
				res.onStorageLoad(_context, id, res_id, data);
            } catch (Exception e){
                printException(e);
                return null;
            }

            writeToCache(id, res);
            res.setId(id);

            addReferenceToOwner(owner, res);
            return res;
		}
	}

    public CompressedResource factoryCompressed(Class<? extends CompressedResource> proto, Entity owner, String id, String location, Object data){
        synchronized(_lock){
            if(null == id){
                id = location;
            }
            // Check if already loaded
            CompressedResource res = (CompressedResource) readFromCache(id);
            if (checkAndAddReferenceToOwner(owner, res)) return res;

            // If not loaded, read from storage
            try {
                res = proto.newInstance();



                if(!res.onCompressedStorageLoad(_context, this, id, _expansionPack, location, data)){
                    Log.e("ShadingZen", "Unable to load compressed resource with id=" + id + " at location=" + location);
                    return null;
                }
            } catch (Exception e){
                printException(e);
                return null;
            }

            writeToCache(id, res);
            res.setId(id);

            addReferenceToOwner(owner, res);
            return res;
        }
    }

    private boolean checkAndAddReferenceToOwner(Entity owner, Resource res) {
        if(null != res){
            addReferenceToOwner(owner, res);
            return true;
        }
        return false;
    }

    private void addReferenceToOwner(Entity owner, Resource res) {
        if(null != owner)
            owner.addResource(res);

        res.addRef();
    }

    String generateResourceId(int res_id){
		String id;
		if(-1 == res_id)
			id = String.format("autores_%d", _autoGeneratedResId++);
		else
			id = String.format("genres_%d", res_id);
		
		return id;
	}
	
	/** Register a resource to an entity. Call this if you didn't attach this resource at creation time with
	 * the factory.
	 * @param res Resource we want to attach to an entity
	 * @param owner New owner to attach to
	 * @param id If the resource has no id and null is passed here, a UUID will be generated
	 */
	public void registerResource(Resource res, Entity owner, String id){
		synchronized(_lock){
			if(null != owner)
				owner.addResource(res);
			
			if(null == res.getId()){
				if(null != id)
					res.setId(id);
				else 
					res.setId(UUID.randomUUID().toString());
			}
				
			writeToCache(id, res);
			
			Log.v("ShadingZen", "Resource registered of type:" + res.getClass().getName() + " with id: " + res.getId());
		}
	}
	
	/** Called by the engine to clean resource in runtime */
	public void doCleanUp(){
		/*
		//Log.v("ShadingZen", "ResourcesManager> doCleanUp start");
		synchronized(_lock){
			Object [] values = _resourcesCache.values().toArray();
			//Log.i("ShadingZen", "Rendering " + values.size() + " shapes");
			for(Object val : values){
				Resource res = (Resource)val;
				if(res.needsRelease()){
					Log.i("ShadingZen", "Removing resource of type:" + res.getClass().getName() + " with id: " + res.getId());
					res.onRelease();
					_resourcesCache.remove(res.getId());
				}
			}
		}
		//Log.v("ShadingZen", "ResourcesManager> doCleanUp end");
		 
		 */
	}
	
	public boolean IsDataPaused(){
		synchronized(_lock){
			return this._dataIsPaused;
		}
	}
	
	public void onResumed(){
		Log.v("ShadingZen", "ResourcesManager> onResumed start");
		synchronized(_lock){
			this._dataIsPaused = false;
			Collection<Resource> values = _resourcesCache.values();
			//Log.i("ShadingZen", "Rendering " + values.size() + " shapes");
            for(Resource res : values){

				if(!res.onResumed(_context)){
					Log.v("ShadingZen", "ResourcesManager error calling onResumed for resource");
				}
			}
		}
		Log.v("ShadingZen", "ResourcesManager> onResumed end");
	}
	public void onPaused(){
		Log.v("ShadingZen", "ResourcesManager> onPaused start");
		synchronized(_lock){
			this._dataIsPaused = true;
			Collection<Resource> values = _resourcesCache.values();
			//Log.i("ShadingZen", "Rendering " + values.size() + " shapes");

			for(Resource res : values){
				Log.v("ShadingZen", "Pausing resource in driver...");
				if(!res.onPaused(_context)){
					
				}
			}
		}
		Log.v("ShadingZen", "ResourcesManager> onPaused end");
	}
	
	/** For each cached resource, reload data into the renderer thread
	 * 
	 * This will cause a data movement to the OpenGL driver
	 */
	public void loadAllToRenderer(){
		synchronized(_lock){
			for(int index=0; index < _cachedResources.length; index++){
                Resource res = (Resource)_cachedResources[index];
				
				//Resource res = iter.next();
				if(res.isDriverDataDirty()){
					//Log.i("ShadingZen", "loading resource of type: " + res.getClass().getName() + " with name: " + res.getId());
					res.onDriverLoad(_context);
				}

					
			}
		}
	}
	
	/** Given a resource ID, load it as an ASCII string.
     * Used to load vertex and fragment shader
     * @param id The resource id
     * @return The string representation of the resource, or null if not loaded
     */
    public String loadResourceString(int id){
    	Log.v("ShadingZen", "ResourcesManager> loading resource with id: " + id);
		InputStream input_stream = _context.getResources().openRawResource(id);
		int size = 0;
		try{
			size = input_stream.available();
		} catch(Exception e){
			
		
		}

		byte[] buffer = new byte[size];
		java.util.Arrays.fill(buffer,(byte)0);
		int read = 0;
		try {
			read = input_stream.read(buffer);
		} catch (IOException e) {
			
			e.printStackTrace();
		}
		
		String _ret = null;
		if(read > 0){
			_ret =  new String(buffer, Charset.forName("ASCII"));
		} else{
			Log.e("loadResourceString"," Unalbe to load resource id=" + id);
		}
		
		return _ret;
	}
}
