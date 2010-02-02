package org.cipango.media.api;

import java.util.List;

/**
 * Synchronizes objects from several sources.
 * 
 * This class maintains a list of sources. Each source has its own list of
 * objects. Thus, when an object is received from one source, a reference to
 * this object is stored in the corresponding list of objects. Identically,
 * when another source provides another object, probably from another thread,
 * this object is stored in another list of objects. The first object added
 * to a source using {@link #put(int, Object)} is considered as the
 * oldest object for this source. Then, when an object consumer has to retrieve
 * the list of oldest objects from each source at the same time, it invokes
 * {@link #getOldests()}. Once retrieved, those old objects are removed from
 * their corresponding source and the second object added using
 * {@link #put(int, Object)} becomes the oldest one, etc.
 * <p>
 * Thus, using this class, you can synchronize {@link RtpPacket}s, buffers,
 * etc. The user of this class has to maintain coherence amongst objects
 * synchronized by this SourceSynchronizer. No check is performed those
 * objects within this class. For example, if {@link RtpPacket}s are
 * synchronized, the similarity of each source codec is not verified here.
 * 
 * @author yohann
 */
public class SourceSynchronizer<T>
{

	/**
	 * Add a source. Each source is identified using a simple integer.
	 * 
	 * @param id the source identifier.
	 */
	public void addSource(int id)
	{
		
	}

	/**
	 * Remove a source. Each source is identified using an integer. When a
	 * source is removed, all objects added using {@link #addSource(int)}
	 * with this source identifier are ignored.
	 * 
	 * @param id the identifier of the source to remove.
	 */
	public void removeSource(int id)
	{
		
	}

	/**
	 * Add an object to a source. Objects added do not have to be unique
	 * for each source. It means that the same object can be added several
	 * times using {@link #addSource(int)}.
	 * 
	 * @param sourceId the identifier of the source where the object is coming
	 * from.
	 * @param t the object that is coming from this source.
	 */
	public void put(int sourceId, T t)
	{
		
	}

	/**
	 * Retrieve the list of oldest object for each source, if available. The
	 * oldest object is the first object that has been added to a source using
	 * {@link #put(int, Object)}.
	 * 
	 * @return the list of oldest objects for each source. If no object is
	 * available returns an empty list.
	 */
	public List<T> getOldests()
	{
		return null;
	}

}