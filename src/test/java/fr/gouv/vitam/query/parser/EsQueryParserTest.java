package fr.gouv.vitam.query.parser;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.query.construct.Query;
import fr.gouv.vitam.query.construct.request.BooleanRequest;
import fr.gouv.vitam.query.construct.request.ExistsRequest;
import fr.gouv.vitam.query.construct.request.InRequest;
import fr.gouv.vitam.query.construct.request.PathRequest;
import static fr.gouv.vitam.query.construct.RequestHelper.*;
import fr.gouv.vitam.query.exception.InvalidParseOperationException;
import fr.gouv.vitam.query.parser.ParserTokens.FILTERARGS;
import fr.gouv.vitam.query.parser.ParserTokens.REQUEST;

public class EsQueryParserTest {
    private static final String exampleBothEsMd = 
            "{ $query : [ { $path : [ 'id1', 'id2'] },"+
                "{ $and : [ {$exists : 'mavar1'}, {$missing : 'mavar2'}, {$isNull : 'mavar3'}, { $or : [ {$in : { 'mavar4' : [1, 2, 'maval1'] }}, { $nin : { 'mavar5' : ['maval2', true] } } ] } ] },"+
                "{ $not : [ { $size : { 'mavar5' : 5 } }, { $gt : { 'mavar6' : 7 } }, { $lte : { 'mavar7' : 8 } } ] , $depth : 5},"+
                "{ $nor : [ { $eq : { 'mavar8' : 5 } }, { $ne : { 'mavar9' : 'ab' } }, { $range : { 'mavar10' : { $gte : 12, $lte : 20} } } ], $relativedepth : 5},"+
                "{ $match_phrase : { 'mavar11' : 'ceci est une phrase' }, $relativedepth : 0},"+
                "{ $match_phrase_prefix : { 'mavar11' : 'ceci est une phrase', $max_expansions : 10 }, $relativedepth : 0},"+
                "{ $flt : { $fields : [ 'mavar12', 'mavar13' ], $like : 'ceci est une phrase' }, $relativedepth : 1},"+
                "{ $and : [ {$search : { 'mavar13' : 'ceci est une phrase' } }, {$regex : { 'mavar14' : '^start?aa.*' } } ] },"+
                "{ $and : [ { $term : { 'mavar14' : 'motMajuscule', 'mavar15' : 'simplemot' } } ] },"+
                "{ $and : [ { $term : { 'mavar16' : 'motMajuscule', 'mavar17' : 'simplemot' } }, { $or : [ {$eq : { 'mavar19' : 'abcd' } }, { $match : { 'mavar18' : 'quelques mots' } } ] } ] },"+
                "{ $regex : { 'mavar14' : '^start?aa.*' } }"+
                "], "+
                "$filter : {$offset : 100, $limit : 1000, $hint : ['cache'], $orderby : { maclef1 : 1 , maclef2 : -1,  maclef3 : 1 } },"+ 
                "$projection : {$fields : {@dua : 1, @all : 1}, $usage : 'abcdef1234' } }";
    
    @Test
    public void testParse() {
        try {
            EsQueryParser command1 = new EsQueryParser(true);
            command1.parse(exampleBothEsMd);
            assertNotNull(command1);
            Query query = new Query();
            query.addRequests(new PathRequest("id1", "id2"));
            query.addRequests(new BooleanRequest(REQUEST.and).
                    addToBooleanRequest(new ExistsRequest(REQUEST.exists, "mavar1"), 
                            new ExistsRequest(REQUEST.missing, "mavar2"),
                            new ExistsRequest(REQUEST.isNull, "mavar3"),
                            new BooleanRequest(REQUEST.or).
                                addToBooleanRequest(new InRequest(REQUEST.in, "mavar4", 1, 2).addInValue("maval1"),
                                        new InRequest(REQUEST.nin, "mavar5", "maval2").addInValue(true))));
            query.addRequests(not().addToBooleanRequest(size("mavar5", 5), 
                    gt("mavar6", 7),
                    lte("mavar7", 8)).setExactDepthLimit(5));
            query.addRequests(nor().addToBooleanRequest(eq("mavar8", 5), 
                    ne("mavar9", "ab"),
                    range("mavar10", 12, true, 20, true)).setRelativeDepthLimit(5));
            query.addRequests(matchPhrase("mavar11", "ceci est une phrase").setRelativeDepthLimit(0));
            query.addRequests(matchPhrasePrefix("mavar11", "ceci est une phrase").setMatchMaxExpansions(10).setRelativeDepthLimit(0));
            query.addRequests(flt("ceci est une phrase", "mavar12", "mavar13").setRelativeDepthLimit(1));
            query.addRequests(and().addToBooleanRequest(search("mavar13", "ceci est une phrase"),
                    regex("mavar14", "^start?aa.*")));
            query.addRequests(and().addToBooleanRequest(term("mavar14", "motMajuscule").addTermRequest("mavar15","simplemot")));
            query.addRequests(and().addToBooleanRequest(term("mavar16", "motMajuscule").addTermRequest("mavar17","simplemot"),
                    or().addToBooleanRequest(eq("mavar19", "abcd"), match("mavar18", "quelques mots"))));
            query.addRequests(regex("mavar14", "^start?aa.*"));

            query.setLimitFilter(100, 1000).addHintFilter(FILTERARGS.cache.exactToken()).
                addOrderByAscFilter("maclef1").addOrderByDescFilter("maclef2").addOrderByAscFilter("maclef3");
            query.addUsedProjection("@dua", "@all").setUsageProjection("abcdef1234");
            EsQueryParser command = new EsQueryParser(true);
            command.parse(query.getFinalQuery().toString());
            assertNotNull(command);
            List<TypeRequest> request1 = command1.getRequests();
            List<TypeRequest> request = command.getRequests();
            for (int i = 0; i < request1.size(); i++) {
				assertTrue("TypeRequest should be equald", request1.get(i).toString().equals(request.get(i).toString()));
			}
            assertTrue("Projection should be equal", command1.projection.toString().equals(command.projection.toString()));
            assertTrue("OrderBy should be equal", command1.orderBy.toString().equals(command.orderBy.toString()));
            assertTrue("ContractId should be equal", command1.contractId.equals(command.contractId));
            assertEquals(command1.hintCache, command.hintCache);
            assertEquals(command1.lastDepth, command.lastDepth);
            assertEquals(command1.limit, command.limit);
            assertEquals(command1.offset, command.offset);
            assertTrue("Command should be equal", command1.toString().equals(command.toString()));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testFilterParse() {
        EsQueryParser command = new EsQueryParser(true);
        Query query = new Query();
        try {
            // empty 
            command.filterParse(query.getFilter());
            assertFalse("Hint should be false", command.hintCache);
            assertEquals(0, command.limit);
            assertEquals(0, command.offset);
            assertNull("OrderBy should be null", command.orderBy);
            // hint set
            query.addHintFilter(FILTERARGS.cache.exactToken());
            command.filterParse(query.getFilter());
            assertTrue("Hint should be True", command.hintCache);
            // hint reset
            query.resetHintFilter();
            command.filterParse(query.getFilter());
            assertFalse("Hint should be false", command.hintCache);
            // hint set false
            query.addHintFilter(FILTERARGS.nocache.exactToken());
            command.filterParse(query.getFilter());
            assertFalse("Hint should be false", command.hintCache);
            // hint unset
            query.resetHintFilter();
            command.filterParse(query.getFilter());
            assertFalse("Hint should be false", command.hintCache);
            // limit set
            query.setLimitFilter(0, 1000);
            command.filterParse(query.getFilter());
            assertEquals(1000, command.limit);
            assertEquals(0, command.offset);
            // offset set
            query.setLimitFilter(100, 0);
            command.filterParse(query.getFilter());
            assertEquals(100, command.offset);
            // orderBy set through array
            query.addOrderByAscFilter("var1", "var2").addOrderByDescFilter("var3");
            command.filterParse(query.getFilter());
            assertNotNull(command.orderBy);
            // check both
            assertEquals(3, command.orderBy.size());
            for (Iterator<Entry<String, JsonNode>> iterator = command.orderBy.fields(); iterator.hasNext();) {
                Entry<String, JsonNode> entry = iterator.next();
                if (entry.getKey().equals("var1")) {
                    assertEquals(1, entry.getValue().asInt());
                }
                if (entry.getKey().equals("var2")) {
                    assertEquals(1, entry.getValue().asInt());
                }
                if (entry.getKey().equals("var3")) {
                    assertEquals(-1, entry.getValue().asInt());
                }
            }
            // orderBy set through composite
            query.resetOrderByFilter();
            command.filterParse(query.getFilter());
            assertNull("OrderBy should be null", command.orderBy);
        } catch (InvalidParseOperationException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testProjectionParse() {
        EsQueryParser command = new EsQueryParser(true);
        Query query = new Query();
        try {
            // empty rootNode
            command.projectionParse(query.getProjection());
            assertNull("Projection should be null", command.projection);
            assertNull("ContractId should be null", command.contractId);
            // contractId set
            query.setUsageProjection("abcd");
            command.projectionParse(query.getProjection());
            assertNotNull("ContractId should not be null", command.contractId);
            // projection set but empty
            query.addUsedProjection((String) null);
            // empty set
            command.projectionParse(query.getProjection());
            assertNotNull("Projection should not be null", command.projection);
            assertEquals(0, command.projection.size());
            // not empty set
            query.addUsedProjection("var1").addUnusedProjection("var2");
            command.projectionParse(query.getProjection());
            assertNotNull("Projection should not be null", command.projection);
            assertEquals(2, command.projection.size());
            for (Iterator<Entry<String, JsonNode>> iterator = command.projection.fields(); iterator.hasNext();) {
                Entry<String, JsonNode> entry = iterator.next();
                if (entry.getKey().equals("var1")) {
                    assertEquals(1, entry.getValue().asInt());
                }
                if (entry.getKey().equals("var2")) {
                    assertEquals(0, entry.getValue().asInt());
                }
            }
        } catch (InvalidParseOperationException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
