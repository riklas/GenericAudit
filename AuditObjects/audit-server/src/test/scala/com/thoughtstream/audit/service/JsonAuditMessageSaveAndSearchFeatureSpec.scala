package com.thoughtstream.audit.service

import com.github.simplyscala.MongodProps
import com.mongodb.casbah.{MongoCollection, MongoConnection}
import com.thoughtstream.audit.bean.MongoDBInstance
import com.thoughtstream.audit.process.JsonAuditMessageProcessor._
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfter, FeatureSpec, GivenWhenThen}
import play.api.libs.json._

/**
 *
 * @author Sateesh
 * @since 27/12/2014
 */

class JsonAuditMessageSaveAndSearchFeatureSpec
  extends FeatureSpec with BeforeAndAfter with GivenWhenThen with StrictLogging {
  info("As a Generic Audit application owner")
  info("I want to save json to a document store")
  info("And I want to be able to search with Xpath based queries.")
  info("searching through collection can be ignored for now")

  var mongoProps: MongodProps = null

  val serviceEndpoint = ("localhost", 27227)
  val databaseName = "AuditObjects"

  val mongoDbInstance = new MongoDBInstance(serviceEndpoint, databaseName)
  val jsonStore = MongoAuditMessageStoringService(mongoDbInstance)
  val searchService = new MongoBasedAuditSearchService(mongoDbInstance)
  var xpathCollection: MongoCollection = null

  before {
    logger.debug("starting the server")
    MongoEmbeddedServer.start()
    val collection = MongoConnection(serviceEndpoint._1, serviceEndpoint._2)(databaseName)("defCollection")
    xpathCollection = MongoConnection(serviceEndpoint._1, serviceEndpoint._2)(databaseName)("xpaths")
    collection.drop()
    xpathCollection.drop()
  }

  after {
    //clean up
    val collection = MongoConnection(serviceEndpoint._1, serviceEndpoint._2)(databaseName)("defCollection")
    collection.drop()
    xpathCollection.drop()
  }

  feature("Audit Save & Search Services") {

    scenario("Simple save & retrieve with a x path") {
      Given("Saved a json that has few properties")
      val newObj = <entity name="user">
        <primitive name="eId" value="johnf"/>
        <primitive name="eType" value="user"/>
        <primitive name="uid" value="456" numeric="true"/>
        <primitive name="ssn" value="DB123 12S"/>
      </entity>

      val response = process(newObj)
      val savedJson: String = response.jsonResponse

      jsonStore.save(AuditSaveRequest(XMLDataSnapshot(newObj), AuditMetaData("robind")))
      logger.info("saved json: "+ Json.prettyPrint(Json.parse(savedJson)))

      When("Query with xpaths")
      var result = searchService.search("/user")

      Then("Search results contain the saved json")
      assert(result.size === 1)
      logger.info("search result: "+Json.prettyPrint(result.head.document))

      When("Simple query with wrong values")
      result = searchService.search("/user=johnf1")
      Then("Result is empty")
      assert(result.size === 0)

      //xpaths count
      logger.info("saved xpaths: " + xpathCollection.find().toSet)
      assert(xpathCollection.find().size === 5)
    }

    scenario("Search queries with x paths") {

      Given("Saved a json that is generated by comparing two object snapshots.")
      val oldObj = <entity name="user">
        <primitive name="eId" value="johnf"/>
        <primitive name="eType" value="user"/>
        <primitive name="uid" value="123" numeric="true"/>
        <primitive name="uidWife" value="123" numeric="true"/>
        <collection name="yearsOfEmployment">
          <primitive name="1" value="2005" numeric="TRUE"/>
          <primitive name="2" value="2001" numeric="TRUE"/>
        </collection>
      </entity>
      val newObj = <entity name="user">
        <primitive name="eId" value="johnf"/>
        <primitive name="eType" value="user"/>
        <primitive name="uid" value="456" numeric="true"/>
        <primitive name="uidWife" value="123" numeric="true"/>
        <collection name="yearsOfEmployment">
          <primitive name="1" value="2010" numeric="TRUE"/>
          <primitive name="2" value="2011" numeric="TRUE"/>
        </collection>
      </entity>

      val response = process(newObj, oldObj)
      val savedJson: String = response.jsonResponse

      jsonStore.save(AuditSaveRequest(XMLDataSnapshot(newObj, oldObj), AuditMetaData("mathewa")))
      logger.info("saved json: "+ Json.prettyPrint(Json.parse(savedJson)))

      When("Query with xpaths(/user)")
      var result = searchService.search("/user")

      Then("Search results contain the saved json")
      assert(result.size === 1)
      logger.info("search result: "+Json.prettyPrint(result.head.document))

      When("Query with xpath and a value(/user/eId=johnf)")
      result = searchService.search("/user/eId=johnf")

      Then("Search results contain the saved json")
      assert(result.size === 1)
      logger.info("search result: "+Json.prettyPrint(result.head.document))

      When("Search with composite Query and predicate is AND(/user/uid=123&&/user/eId=JOHNF)")
      result = searchService.search("/user/uid=123&&/user/eId=JOHNF")

      Then("Result contains the saved json")
      assert(result.size === 1)
      logger.info("search result: "+Json.prettyPrint(result.head.document))

      When("composite Query with wrong values")
      result = searchService.search("/user/uidWife=123&&/user/eId=johnf12")

      Then("Result is empty")
      assert(result.size === 0)

      When("Search with composite Query and predicate is OR (/user/uidWife=1234++/user/eId=johnf)")
      result = searchService.search("/user/uidWife=1234++/user/eId=johnf")

      Then("Result contains the saved json")
      assert(result.size === 1)
      logger.info("search result: "+Json.prettyPrint(result.head.document))

      When("Search with composite Query and predicate is OR")
      result = searchService.search("/user/uidWife=123++/user/eId=johnf12")

      Then("Result contains the saved json")
      assert(result.size === 1)
      logger.info("search result: "+Json.prettyPrint(result.head.document))

      When("composite Query with nested values (/user=johnf/uidWife=123)")
      result = searchService.search("/user=johnf/uidWife=123++/user/eId=johnf12")

      Then("Result contains the saved json")
      assert(result.size === 1)
      logger.info("search result: "+Json.prettyPrint(result.head.document))

      When("nested query with wrong values")
      result = searchService.search("/user=johnf1/uidWife=123")

      Then("Result is empty")
      assert(result.size === 0)

      // like
      When("simple query with like(/user/eId=joh%)")
      result = searchService.search("/user/eId=joh%")

      Then("Result contains the saved json")
      assert(result.size === 1)
      logger.info("search result: "+Json.prettyPrint(result.head.document))

      When("composite query with like(/user/eId=%hnf++/user/test=abc)")
      result = searchService.search("/user/eId=%hnf++/user/test=abc")

      Then("Result contains the saved json")
      assert(result.size === 1)
      logger.info("search result: "+Json.prettyPrint(result.head.document))

      When("composite query with like")
      result = searchService.search("/user/eId=%h%++/user/test=abc")

      Then("Result contains the saved json")
      assert(result.size === 1)
      logger.info("search result: "+Json.prettyPrint(result.head.document))

      When("nested Query with like (/user=johnf/eId=john%)")
      result = searchService.search("/user=johnf/eId=john%")

      Then("Result contains the saved json")
      assert(result.size === 1)
      logger.info("search result: "+Json.prettyPrint(result.head.document))

      When("composite query with like with wrong values")
      result = searchService.search("/user/eId=%qw%++/user/test=abc")

      Then("Result is empty")
      assert(result.size === 0)
    }
  }
}
