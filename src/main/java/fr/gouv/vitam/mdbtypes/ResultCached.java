/**
   This file is part of POC MongoDB ElasticSearch Project.

   Copyright 2009, Frederic Bregier, and individual contributors by the @author
   tags. See the COPYRIGHT.txt in the distribution for a full listing of
   individual contributors.

   All POC MongoDB ElasticSearch Project is free software: you can redistribute it and/or 
   modify it under the terms of the GNU General Public License as published 
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   POC MongoDB ElasticSearch is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with POC MongoDB ElasticSearch .  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.gouv.vitam.mdbtypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BSONObject;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import fr.gouv.vitam.utils.UUID;

/**
 * @author "Frederic Bregier"
 *
 */
public class ResultCached extends VitamType {
	private static final long serialVersionUID = 5962911495483495562L;

	public static final String CURRENTMAIP = "__cmaip";
	public static final String PREVIOUSMAIP = "__pmaip";
	public static final String MINLEVEL = "__min";
	public static final String MAXLEVEL = "__max";
	public static final String NBSUBNODES = "__nbnd";
	
	public Set<String> currentMaip = new HashSet<String>();
	private Set<String> previousMaip = new HashSet<String>();
	public int minLevel = 0, maxLevel = 0;
	public long nbSubNodes = -1;
	public boolean loaded = false;
	
	/* (non-Javadoc)
	 * @see java.util.LinkedHashMap#clear()
	 */
	@Override
	public void clear() {
		super.clear();
		currentMaip.clear();
		previousMaip.clear();
		minLevel = 0;
		maxLevel = 0;
		nbSubNodes = -1;
		loaded = false;
	}
	@Override
	public void getAfterLoad() {
		super.getAfterLoad();
		if (this.containsField(CURRENTMAIP)) {
			@SuppressWarnings("unchecked")
			List<String> list = (List<String>) this.get(CURRENTMAIP);
			currentMaip.clear();
			currentMaip.addAll(list);
		}
		if (this.containsField(PREVIOUSMAIP)) {
			@SuppressWarnings("unchecked")
			List<String> list = (List<String>) this.get(PREVIOUSMAIP);
			previousMaip.clear();
			previousMaip.addAll(list);
		}
		minLevel = this.getInt(MINLEVEL, 0);
		maxLevel = this.getInt(MAXLEVEL, 0);
		nbSubNodes = this.getLong(NBSUBNODES, -1);
	}
	@Override
	public void putBeforeSave() {
		super.putBeforeSave();
		if (! currentMaip.isEmpty()) {
			this.put(CURRENTMAIP, currentMaip);
		}
		if (! previousMaip.isEmpty()) {
			this.put(PREVIOUSMAIP, previousMaip);
		}
		this.put(MINLEVEL, minLevel);
		this.put(MAXLEVEL, maxLevel);
		this.put(NBSUBNODES, nbSubNodes);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected boolean updated(MongoDbAccess dbvitam) {
		ResultCached vt = (ResultCached) dbvitam.requests.collection.findOne(new BasicDBObject(ID, get(ID)));
		if (vt != null) {
			List<DBObject> list = new ArrayList<DBObject>();
			List<String> slist = (List<String>) vt.get(CURRENTMAIP);
			if (slist != null) {
				Set<String> newset = new HashSet<String>(this.currentMaip);
				newset.removeAll(currentMaip);
				if (! newset.isEmpty()) {
					list.add(new BasicDBObject(CURRENTMAIP, new BasicDBObject("$each", newset)));
				}
			}
			slist = (List<String>) vt.get(PREVIOUSMAIP);
			if (slist != null) {
				Set<String> newset = new HashSet<String>(this.previousMaip);
				newset.removeAll(previousMaip);
				if (! newset.isEmpty()) {
					list.add(new BasicDBObject(PREVIOUSMAIP, new BasicDBObject("$each", newset)));
				}
			}
			if (! list.isEmpty()) {
				BasicDBObject upd = new BasicDBObject();
				for (DBObject dbObject : list) {
					upd.putAll(dbObject);
				}
				upd.append(MINLEVEL, minLevel);
				upd.append(MAXLEVEL, maxLevel);
				upd.append(NBSUBNODES, nbSubNodes);
				BasicDBObject update = new BasicDBObject("$addToSet", upd);
				dbvitam.requests.collection.update(new BasicDBObject(ID, this.get(ID)), update);
			}
		}
		return false;
	}
	
	@Override
	public void save(MongoDbAccess dbvitam) {
		putBeforeSave();
		if (updated(dbvitam)) return;
		updateOrSave(dbvitam.requests);
		loaded = true;
	}
	@Override
	public void load(MongoDbAccess dbvitam) {
		ResultCached vt = (ResultCached) dbvitam.requests.collection.findOne(new BasicDBObject(ID, get(ID)));
		this.putAll((BSONObject) vt);
		loaded = true;
	}
	/**
	 * Compute min and max from list of UUID in currentMaip.
	 * Note: this should not be called from a list of "short" UUID, but only with "path" UUIDs
	 */
	public void updateMinMax() {
		minLevel = 0;
		maxLevel = 0;
		for (String id : currentMaip) {
			int level = UUID.getUuidNb(id);
			if (minLevel > level) {
				minLevel = level;
			}
			if (maxLevel == 0 || maxLevel < level) {
				maxLevel = level;
			}
		}
	}
	/**
	 * Compute min and max from list of real MAIP (from UUID), so loaded from database (could be heavy)
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	public void updateLoadMinMax(MongoDbAccess dbvitam) throws InstantiationException, IllegalAccessException {
		minLevel = 0;
		maxLevel = 0;
		for (String id : currentMaip) {
			int level = UUID.getUuidNb(id);
			if (UUID.getUuidNb(id) == 1) {
				DAip daip = DAip.findOne(dbvitam, id);
				if (daip == null) {
					continue;
				}
				Map<String, Integer> domdepth = daip.getDomDepth();
				if (domdepth == null || domdepth.isEmpty()) {
					level = 1;
				} else {
					level = Integer.MAX_VALUE;
					for (int lev : domdepth.values()) {
						if (level > lev) {
							level = lev;
						}
					}
					level++;
				}
			}
			if (minLevel > level) {
				minLevel = level;
			}
			if (maxLevel == 0 || maxLevel < level) {
				maxLevel = level;
			}
		}
	}
	
	public static void addIndexes(MongoDbAccess dbvitam) {
		//dbvitam.requests.collection.createIndex(new BasicDBObject(MongoDbAccess.VitamLinks.DAip2PAip.field2to1, 1));
	}
}
