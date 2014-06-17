/**
 * This file is part of Vitam Project.
 * 
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author tags. See the
 * COPYRIGHT.txt in the distribution for a full listing of individual contributors.
 * 
 * All Vitam Project is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Vitam is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Vitam . If not, see
 * <http://www.gnu.org/licenses/>.
 */
package fr.gouv.vitam.query.parser;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.gouv.vitam.query.exception.InvalidParseOperationException;
import fr.gouv.vitam.query.json.JsonHandler;
import fr.gouv.vitam.query.parser.ParserTokens.RANGEARGS;
import fr.gouv.vitam.query.parser.ParserTokens.REQUEST;

/**
 * Version using MongoDB and ElasticSearch
 * 
 * @author "Frederic Bregier"
 * 
 */
public class MdEsQueryParser extends EsQueryParser {
	public MdEsQueryParser(boolean simul) {
		super(simul);
		usingMongoDb = true;
	}
	
	/*
	 * Here are 3 variations: 

* Query only: 

   { query: { text: {  _all: "foo bar" }}} 


* Filter only: 

   { query: { 
         constant_score: { 
             filter: {  term: { status: "open" }} 
         } 
   }} 

* Query and Filter: 

   { query: { 
         filtered: { 
             query:  {  text: { _all:   "foo bar"}} 
             filter: {  term: { status: "open" }} 
         } 
   }} 


So: 
--- 
1) You always need wrap your query in a top-level query element 
2) A "constant_score" query says "all docs are equal", so no scoring 
   has to happen - just the filter gets applied 
3) In the third example, filter reduces the number of docs that 
   can be matched (and scored) by the query 


There is also a top-level filter argument: 

{ 
  query:  { text: { _all: "foo bar" }}, 
  filter: { term: { status: "open" }} 
} 

For normal usage, you should NOT use this version.  It's purpose is 
different from the "filtered" query mentioned above. 

This is intended only to be used when you want to: 
 - run a query 
 - filter the results 
 - BUT show facets on the UNFILTERED results 

So this filter will be less efficient than the "filtered" query. 
	 */
	/*
		In MongoDB : find(Query, Projection).sort(SortFilter).skip(SkipFilter).limit(LimitFilter);
		In addition, one shall limit the scan by: find(Query, Projection)._addSpecial( "$maxscan", highlimit ).sort(SortFilter).skip(SkipFilter).limit(LimitFilter);
	
	 	In ElasticSearch : 
	 	Query => { "from" : offset, "size" : number, "sort" : [ SortFilter as "name" : { "order" : "asc|desc" } ], "query" : Query }
	 	FilteredQuery => { "filtered" : { "query" : { Query }, "filter" : { "limit" : { "value" : limit } } } }
 	 
	 */

	/**
	 * $size : { name : length }
	 * @param refCommand
	 * @param command
	 * @param tr0
	 * @param req
	 * @throws InvalidParseOperationException
	 */
	protected void analyzeSize(String refCommand, JsonNode command, TypeRequest tr0,
			REQUEST req) throws InvalidParseOperationException {
		if (command == null) {
			throw new InvalidParseOperationException("Not correctly parsed: "+refCommand);
		}
		Entry<String, JsonNode> element = JsonHandler.checkUnicity(refCommand, command);
		sizeEs(tr0, element);
		tr0.requestModel[MONGODB] = JsonHandler.createObjectNode();
		tr0.requestModel[MONGODB].putObject(element.getKey()).set(refCommand, element.getValue());
	}

	/**
	 * $gt : { name : value }
	 * @param refCommand
	 * @param command
	 * @param tr0
	 * @param req
	 * @throws InvalidParseOperationException
	 */
	protected void analyzeCompare(String refCommand, JsonNode command, TypeRequest tr0,
			REQUEST req) throws InvalidParseOperationException {
		if (command == null) {
			throw new InvalidParseOperationException("Not correctly parsed: "+refCommand);
		}
		Entry<String, JsonNode> element = JsonHandler.checkUnicity(refCommand, command);
		compareEs(tr0, req, element);
		tr0.requestModel[MONGODB] = JsonHandler.createObjectNode();
		tr0.requestModel[MONGODB].putObject(element.getKey()).set(refCommand, element.getValue());
	}

	/**
	 * $in : { name : [ value1, value2, ... ] }
	 * @param refCommand
	 * @param command
	 * @param tr0
	 * @throws InvalidParseOperationException
	 */
	protected void analyzeIn(String refCommand, JsonNode command, TypeRequest tr0,
			REQUEST req)
			throws InvalidParseOperationException {
		if (command == null) {
			throw new InvalidParseOperationException("Not correctly parsed: "+refCommand);
		}
		Entry<String, JsonNode> element = JsonHandler.checkUnicity(refCommand, command);
		inEs(tr0, req, element);
		tr0.requestModel[MONGODB] = JsonHandler.createObjectNode();
		ArrayNode objectMD = tr0.requestModel[MONGODB].putObject(element.getKey()).putArray(refCommand);
		for (JsonNode value : element.getValue()) {
			objectMD.add(value);
		}
	}

	/**
	 * $range : { name : { $gte : value, $lte : value } }
	 * @param refCommand
	 * @param command
	 * @param tr0
	 * @throws InvalidParseOperationException
	 */
	protected void analyzeRange(String refCommand, JsonNode command, TypeRequest tr0,
			REQUEST req)
			throws InvalidParseOperationException {
		if (command == null) {
			throw new InvalidParseOperationException("Not correctly parsed: "+refCommand);
		}
		Entry<String, JsonNode> element = JsonHandler.checkUnicity(refCommand, command);
		rangeEs(tr0, req, element);
		tr0.requestModel[MONGODB] = JsonHandler.createObjectNode();
		ObjectNode objectMD = tr0.requestModel[MONGODB].putObject(element.getKey());
		for (Iterator<Entry<String, JsonNode>> iterator = element.getValue().fields(); iterator.hasNext();) {
			Entry<String, JsonNode> requestItem = iterator.next();
			RANGEARGS arg = null;
			try {
				String key = requestItem.getKey();
				if (key.startsWith("$")) {
					arg = RANGEARGS.valueOf(requestItem.getKey().substring(1));
				} else {
					throw new InvalidParseOperationException("Invalid Range query command: "+requestItem);
				}
			} catch (IllegalArgumentException e) {
				throw new InvalidParseOperationException("Invalid Range query command: "+requestItem);
			}
			objectMD.set(arg.exactToken(), requestItem.getValue());
		}
	}

	/**
	 * $regex : { name : regex }
	 * @param refCommand
	 * @param command
	 * @param tr0
	 * @param req
	 * @throws InvalidParseOperationException
	 */
	protected void analyzeRegex(String refCommand, JsonNode command, TypeRequest tr0,
			REQUEST req) throws InvalidParseOperationException {
		if (command == null) {
			throw new InvalidParseOperationException("Not correctly parsed: "+refCommand);
		}
		Entry<String, JsonNode> entry = JsonHandler.checkUnicity(refCommand, command);
		regexEs(tr0, entry);
		tr0.requestModel[MONGODB] = JsonHandler.createObjectNode();
		tr0.requestModel[MONGODB].putObject(entry.getKey()).set(refCommand, entry.getValue());
	}

	/**
	 * $term : { name : term, name : term }
	 * 
	 * @param refCommand
	 * @param command
	 * @param tr0
	 * @throws InvalidParseOperationException
	 */
	protected void analyzeTerm(String refCommand, JsonNode command, TypeRequest tr0,
			REQUEST req)
			throws InvalidParseOperationException {
		if (command == null) {
			throw new InvalidParseOperationException("Not correctly parsed: "+refCommand);
		}
		termEs(command, tr0);
		tr0.requestModel[MONGODB] = JsonHandler.createObjectNode();
		tr0.requestModel[MONGODB].setAll((ObjectNode) command);
	}

	/**
	 * $eq : { name : value }
	 * 
	 * @param refCommand
	 * @param command
	 * @param tr0
	 * @throws InvalidParseOperationException
	 */
	protected void analyzeEq(String refCommand, JsonNode command, TypeRequest tr0,
			REQUEST req)
			throws InvalidParseOperationException {
		if (command == null) {
			throw new InvalidParseOperationException("Not correctly parsed: "+refCommand);
		}
		Entry<String, JsonNode> entry = JsonHandler.checkUnicity(refCommand, command);
		eqEs(tr0, req, entry);
		tr0.requestModel[MONGODB] = JsonHandler.createObjectNode();
		if (req == REQUEST.ne) {
			tr0.requestModel[MONGODB].putObject(entry.getKey()).set(refCommand, entry.getValue());
		} else {
			tr0.requestModel[MONGODB].set(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * $exists : name
	 * 
	 * @param refCommand
	 * @param command
	 * @param tr0
	 * @param req
	 * @throws InvalidParseOperationException
	 */
	protected void analyzeExistsMissing(String refCommand, JsonNode command, TypeRequest tr0,
			REQUEST req) throws InvalidParseOperationException {
		if (command == null) {
			throw new InvalidParseOperationException("Not correctly parsed: "+refCommand);
		}
		existsEs(command, tr0, req);
		// only fieldname
		String fieldname = command.asText();
		tr0.requestModel[MONGODB] = JsonHandler.createObjectNode();
		tr0.requestModel[MONGODB].putObject(fieldname).put(REQUEST.exists.exactToken(), req == REQUEST.exists);
	}

	/**
	 * $isNull : name
	 * 
	 * @param refCommand
	 * @param command
	 * @param tr0
	 * @param req
	 * @throws InvalidParseOperationException
	 */
	protected void analyzeIsNull(String refCommand, JsonNode command, TypeRequest tr0,
			REQUEST req) throws InvalidParseOperationException {
		if (command == null) {
			throw new InvalidParseOperationException("Not correctly parsed: "+refCommand);
		}
		isNullEs(command, tr0);
		// only fieldname
		String fieldname = command.asText();
		tr0.requestModel[MONGODB] = JsonHandler.createObjectNode();
		tr0.requestModel[MONGODB].putObject(fieldname).put("$type", 10);
	}
	
	/**
	 * $and : [ expression1, expression2, ... ]
	 * @param refCommand
	 * @param command
	 * @param tr0
	 * @param req
	 * @throws InvalidParseOperationException
	 */
	protected void analyzeAndNotNorOr(String refCommand, JsonNode command, TypeRequest tr0,
			REQUEST req) throws InvalidParseOperationException {
		if (command == null) {
			throw new InvalidParseOperationException("Not correctly parsed: "+refCommand);
		}
		List<TypeRequest> trlist = new ArrayList<>();
		booleanEs(refCommand, command, tr0, req, trlist);
		if (!tr0.isOnlyES) {
			// MD
			tr0.requestModel[MONGODB] = JsonHandler.createObjectNode();
			ArrayNode array = null;
			if (req == REQUEST.not) {
				if (trlist.size() == 1) {
					tr0.requestModel[MONGODB].set(REQUEST.not.exactToken(), trlist.get(0).requestModel[MONGODB]);
				} else {
					array = tr0.requestModel[MONGODB].putObject(REQUEST.not.exactToken()).putArray(REQUEST.and.exactToken());
				}
			} else {
				array = tr0.requestModel[MONGODB].putArray(refCommand);
			}
			if (array != null) {
				for (int i = 0; i < trlist.size(); i++) {
					TypeRequest tr = trlist.get(i);
					if (tr.requestModel[MONGODB] != null) {
						array.add(tr.requestModel[MONGODB]);
					}
				}
			}
		}
	}
}
