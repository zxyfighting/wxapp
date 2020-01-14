package com.faw.hq.dmp.spark.imp.wxapp.inter

import java.sql.{Connection, DriverManager, PreparedStatement}

import com.faw.hq.dmp.spark.imp.wxapp.contanst.KafkaParams
import org.apache.spark.streaming.dstream.DStream

/**
  * @ program: wxapp
  * @ description
  * @ author: ZhangXiuYun
  * @ create: 2020-01-13 17:24
  **/
//将sql操作封装---例如：insert into table (?,?,?) values(Array[params])
object ImplJDBC {
  def executeUpdate(sql: String, params: Array[Any],connection:Connection): Unit = {
    var rtn = 0
    var pstmt: PreparedStatement = null
    try {
      connection.setAutoCommit(false)
      pstmt = connection.prepareStatement(sql)

      if (params != null && params.length > 0) {
        for (i <- 0 until params.length) {
          pstmt.setObject(i + 1, params(i))
        }
      }
      pstmt.executeUpdate()
      connection.commit()
    } catch {
      case e: Exception => e.printStackTrace
    }
  }
//PV,UV参数一致，可以通过封装实现。
  def puvCounts(sql: String,puvResultCounts: DStream[(String, Long)]): Unit = {
    puvResultCounts.foreachRDD(
      cs => {
        var conn: Connection = null;
        var ps: PreparedStatement = null;
        try {
          Class.forName("com.mysql.jdbc.Driver").newInstance();
          cs.foreachPartition(f => {
            var conn = DriverManager.getConnection(KafkaParams.url, KafkaParams.userName, KafkaParams.password);
            f.foreach(s => {
              executeUpdate(sql,Array(s._1,s._2,KafkaParams.dateString1),conn)
            })
          })
        } catch {
          case t: Throwable => t.printStackTrace()
        } finally {
          if (ps != null) {
            ps.close()
          }
          if (conn != null) {
            conn.close();
          }
        }
      })

  }
}