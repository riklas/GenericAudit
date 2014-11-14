package com.thoughtstream.audit.service

import com.github.simplyscala.{MongodProps, MongoEmbedDatabase}
import com.mongodb.casbah.Imports._
import com.mongodb.util.JSON
import com.thoughtstream.audit.bean.MongoDB
import com.thoughtstream.audit.process.JsonAuditMessageProcessor
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.json4s.native.JsonMethods._

/**
 * Integration test
 * @author Sateesh
 * @since 13/11/2014
 */
class MongoAuditMessageStoringServiceTest extends FunSuite with MongoEmbedDatabase with BeforeAndAfter{
  var mongoProps: MongodProps = null

  before {
    mongoProps = mongoStart(27017)   // by default port = 12345 & version = Version.2.3.0
  }                                  // add your own port & version parameters in mongoStart method if you need it

  after { mongoStop(mongoProps) }

  val serviceEndpoint = ("localhost",27017)
  val databaseName = "AuditObjects"

  test("first record save to MongoDb") {
    val processor = JsonAuditMessageProcessor
    val consumer = new MongoDB(serviceEndpoint,databaseName) with MongoAuditMessageStoringService

    val collection = MongoConnection(serviceEndpoint._1, serviceEndpoint._2)(databaseName)("defCollection")

    val oldObj = <entity name="user">
      <primitive name="eId" value="johnf"/>
      <primitive name="eType" value="user"/>
      <primitive name="uid" value="123" numeric="true"/>
      <primitive name="uidWife" value="123" numeric="true"/>
    </entity>
    val newObj = <entity name="user">
      <primitive name="eId" value="johnf"/>
      <primitive name="eType" value="user"/>
      <primitive name="uid" value="456" numeric="true"/>
      <primitive name="uidWife" value="123" numeric="true"/>
    </entity>

    consumer.save(processor.process(newObj, oldObj))
    val userOptional = collection.findOne(JSON.parse("{'user.eId': 'johnf'}").asInstanceOf[DBObject])

    val result = parse(userOptional.get.toString)
    assert((result \ "user" \ "eId").values === "johnf")

    //tear down
    collection.remove(MongoDBObject())
  }
}
