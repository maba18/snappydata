/*
 * Copyright (c) 2016 SnappyData, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package org.apache.spark.examples.snappydata

import java.io.PrintWriter

import com.typesafe.config.Config
import org.apache.log4j.{Level, Logger}

import org.apache.spark.sql.types.{DecimalType, StringType, IntegerType, StructField, StructType}
import org.apache.spark.sql.{SnappySession, SparkSession, SnappySQLJob, Row, SnappyJobValid, SnappyJobValidation, SnappyContext}

/**
 * An example that shows how to create partitioned row tables in SnappyData
 * using SQL or APIs.
 *
 * <p></p>
 * This example can be run either in local mode(in which it will spawn a single
 * node SnappyData system) or can be submitted as a job to an already running
 * SnappyData cluster.
 *
 * <p></p>
 * To run the example in local mode go to your SnappyData product distribution
 * directory and type following command on the command prompt
 * <pre>
 * bin/run-example snappydata.CreatePartitionedRowTable
 * </pre>
 *
 * To submit this example as a job to an already running cluster
 * <pre>
 *   cd $SNAPPY_HOME
 *   bin/snappy-job.sh submit
 *   --app-name CreatePartitionedRowTable
 *   --class org.apache.spark.examples.snappydata.CreatePartitionedRowTable
 *   --app-jar examples/jars/quickstart.jar
 *   --lead [leadHost:port]
 *
 * Check the status of your job id
 * bin/snappy-job.sh status --lead [leadHost:port] --job-id [job-id]
 *
 * The output of the job will be redirected to a file named CreatePartitionedRowTable.out
 *
 */
object CreatePartitionedRowTable extends SnappySQLJob {

  case class Data(PS_PARTKEY: Int, PS_SUPPKEY: Int,
      PS_AVAILQTY: Int, PS_SUPPLYCOST: BigDecimal)

  override def runSnappyJob(snc: SnappyContext, jobConfig: Config): Any = {

    val pw = new PrintWriter("CreatePartitionedRowTable.out")

    createPartitionedRowTableUsingSQL(snc, pw)

    createPartitionedRowTableUsingAPI(snc, pw)

    pw.close()
  }

  override def isValidJob(sc: SnappyContext, config: Config): SnappyJobValidation = SnappyJobValid()

  /**
   * Creates a partitioned row table and performs operations on it using APIs
   */
  def createPartitionedRowTableUsingAPI(snc: SnappyContext, pw: PrintWriter): Unit = {
    pw.println()
    pw.println("****Create a partitioned row table using API****")
    pw.println()
    pw.println("Creating a partitioned row table(PARTSUPP) using API")

    // drop the table if it exists
    snc.dropTable("PARTSUPP", ifExists = true)

    val schema = StructType(Array(StructField("PS_PARTKEY", IntegerType, false),
      StructField("S_SUPPKEY", IntegerType, false),
      StructField("PS_AVAILQTY", IntegerType, false),
      StructField("PS_SUPPLYCOST", DecimalType(15, 2), false)
    ))

    // props1 map specifies the properties for the table to be created
    // "PARTITION_BY" attribute specifies partitioning key for PARTSUPP table(PS_PARTKEY),
    // "BUCKETS" attribute specifies the smallest unit that can be moved around
    // in SnappyStore when the data migrates. Here we specify
    // the table to have 11 buckets
    val props1 = Map("PARTITION_BY" -> "PS_PARTKEY", "BUCKETS" -> "11")
    snc.createTable("PARTSUPP", "row", schema, props1)

    pw.println("Inserting data in PARTSUPP table")
    val data = Seq(Seq(100, 1, 5000, BigDecimal(100)),
      Seq(200, 2, 50, BigDecimal(10)),
      Seq(300, 3, 1000, BigDecimal(20)),
      Seq(400, 4, 200, BigDecimal(30))
    )
    val rdd = snc.sparkContext.parallelize(data,
      data.length).map(s => new Data(s(0).asInstanceOf[Int],
      s(1).asInstanceOf[Int],
      s(2).asInstanceOf[Int],
      s(3).asInstanceOf[BigDecimal]))

    val dataDF = snc.createDataFrame(rdd)
    dataDF.write.insertInto("PARTSUPP")

    pw.println("Printing the contents of the PARTSUPP table")
    var tableData = snc.sql("SELECT * FROM PARTSUPP").collect()
    tableData.foreach(pw.println)

    pw.println()
    pw.println("Update the available quantity for PARTKEY 100")
    snc.update("PARTSUPP", "PS_PARTKEY = 100", Row(50000), "PS_AVAILQTY")

    pw.println("Printing the contents of the PARTSUPP table after update")
    tableData = snc.sql("SELECT * FROM PARTSUPP").collect()
    tableData.foreach(pw.println)

    pw.println()
    pw.println("Delete the records for PARTKEY 400")
    snc.sql("DELETE FROM PARTSUPP WHERE PS_PARTKEY = 400")
    snc.delete("PARTSUPP", "PS_PARTKEY = 400")

    pw.println("Printing the contents of the PARTSUPP table after delete")
    tableData = snc.sql("SELECT * FROM PARTSUPP").collect()
    tableData.foreach(pw.println)

    pw.println("****Done****")
  }

  /**
   * Creates a partitioned row table and performs operations on it using SQL queries
   * thru SnappyContext
   *
   * Other way to execute a SQL statement is thru JDBC or ODBC driver. Refer to
   * JDBCExample.scala for more details
   */
  def createPartitionedRowTableUsingSQL(snc: SnappyContext, pw: PrintWriter): Unit = {
    pw.println()

    pw.println("****Create a row table using SQL****")
    // create a partitioned row table using SQL
    pw.println()
    pw.println("Creating a partitioned row table(PARTSUPP) using SQL")

    snc.sql("DROP TABLE IF EXISTS PARTSUPP")

    // Create the table using SQL command
    // "PARTITION_BY" attribute specifies partitioning key for PARTSUPP table(PS_PARTKEY),
    // "BUCKETS" attribute specifies the smallest unit that
    // can be moved around in SnappyStore when the data migrates. Here we specify
    // the table to have 11 buckets
    // For complete list of table attributes refer the documentation
    snc.sql("CREATE TABLE PARTSUPP ( " +
        "PS_PARTKEY     INTEGER NOT NULL PRIMARY KEY," +
        "PS_SUPPKEY     INTEGER NOT NULL," +
        "PS_AVAILQTY    INTEGER NOT NULL," +
        "PS_SUPPLYCOST  DECIMAL(15,2)  NOT NULL)" +
        "USING ROW OPTIONS (PARTITION_BY 'PS_PARTKEY', BUCKETS '11' )")

    // insert some data in it
    pw.println()
    pw.println("Inserting data in PARTSUPP table")
    snc.sql("INSERT INTO PARTSUPP VALUES(100, 1, 5000, 100)")
    snc.sql("INSERT INTO PARTSUPP VALUES(200, 2, 50, 10)")
    snc.sql("INSERT INTO PARTSUPP VALUES(300, 3, 1000, 20)")
    snc.sql("INSERT INTO PARTSUPP VALUES(400, 4, 200, 30)")

    pw.println("Printing the contents of the PARTSUPP table")
    var tableData = snc.sql("SELECT * FROM PARTSUPP").collect()
    tableData.foreach(pw.println)

    pw.println()
    pw.println("Update the available quantity for PARTKEY 100")
    snc.sql("UPDATE PARTSUPP SET PS_AVAILQTY = 50000 WHERE PS_PARTKEY = 100")

    pw.println("Printing the contents of the PARTSUPP table after update")
    tableData = snc.sql("SELECT * FROM PARTSUPP").collect()
    tableData.foreach(pw.println)

    pw.println()
    pw.println("Delete the records for PARTKEY 400")
    snc.sql("DELETE FROM PARTSUPP WHERE PS_PARTKEY = 400")

    pw.println("Printing the contents of the PARTSUPP table after delete")
    tableData = snc.sql("SELECT * FROM PARTSUPP").collect()
    tableData.foreach(pw.println)

    pw.println("****Done****")
  }

  def main(args: Array[String]): Unit = {
    // reducing the log level to minimize the messages on console
    Logger.getLogger("org").setLevel(Level.ERROR)
    Logger.getLogger("akka").setLevel(Level.ERROR)

    println("Creating a SnappySession")
    val spark: SparkSession = SparkSession
        .builder
        .appName("CreatePartitionedRowTable")
        .master("local[4]")
        .getOrCreate

    val snSession = new SnappySession(spark.sparkContext, existingSharedState = None)

    val pw = new PrintWriter(System.out, true)
    createPartitionedRowTableUsingSQL(snSession.snappyContext, pw)
    createPartitionedRowTableUsingAPI(snSession.snappyContext, pw)
    pw.close()
  }


}
