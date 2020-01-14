package com.faw.hq.dmp.spark.imp.wxapp.inter

import java.util

import com.faw.hq.dmp.spark.imp.wxapp.contanst.KafkaParams
import com.faw.hq.dmp.spark.imp.wxapp.realize.KafkaConsumer.PeopleVisit
import com.faw.hq.dmp.spark.imp.wxapp.util.JedisPoolUtils
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream

/**
  * @ program: wxapp
  * @ description
  * @ author: ZhangXiuYun
  * @ create: 2020-01-13 17:26
  **/

object ImplPUV {
  //PV计算------根据处理后的数据，将结果累加
  def pvUpdate(rdd: DStream[(String, Int)]): DStream[(String, Long)] = {
    rdd.updateStateByKey {
      case (seq, buffer) => { //seq序列当前周期中数量对集合，buffer表缓冲当中的值，所谓的checkPoint
        val sumCount = seq.sum + buffer.getOrElse(0L)
        Option(sumCount) //表往缓存里边更新对值　　它需要返回一个Option
      }
    }
  }
  //UV计算-----通过redis进行批次间去重
  def filterRedis(rdd: DStream[PeopleVisit], ssc:StreamingContext):DStream[PeopleVisit]  = {
    rdd.transform(rdd=> {
      println("过滤前：" + rdd.count())
      // 获取 redis 连接
      val jedisClient = JedisPoolUtils.getPool.getResource
      val key: String = "uv:" + KafkaParams.dateString
      val midSet: util.Set[String] = jedisClient.smembers(key)
      jedisClient.close()
      //问题：由于没有用广播变量导致，executor底层序列化方式，导致空指针异常。
      val midBC: Broadcast[util.Set[String]] = ssc.sparkContext.broadcast(midSet)
      val filterUser: RDD[PeopleVisit] = rdd.filter(rdd => {
        !midBC.value.contains(rdd.uninid)
      })
      println("过滤后的用户" + filterUser.count())
      filterUser
    })
  }
  //UV计算------通过groupBykey进行批次内去重
  def groupByUnion(value: DStream[PeopleVisit]): DStream[PeopleVisit] = {
    value.map(rdd => (rdd.uninid, rdd)).groupByKey().flatMap {
      case (uninid, uv) => {
        uv.take(1)
      }
    }
  }
 //UV计算------将结果存到redis中
  def distinctRedis(rdd: DStream[PeopleVisit]): Unit = {
    rdd.foreachRDD(rdd => {
      rdd.foreachPartition(uninid => {
        // 获取 redis 连接
        val jedisClient = JedisPoolUtils.getPool.getResource
        for (elem <- uninid) {
          val key = "uv" + elem.dateToday
          jedisClient.sadd(key, elem.uninid)
          //jedisClient.expire(key, timeout.toInt)
          //设置key的过期时间是24小时
          jedisClient.expire(key, KafkaParams.timeout.toInt * 60 * 24)
          //    println(elem.uninid+">>>"+elem.dateToday)
        }
        jedisClient.close()
      })
    })
  }

 //UV计算------根据处理的结果进行累加数据
  def getUsers(rdd: DStream[(String, Long)]): DStream[(String, Long)] = {
    rdd.updateStateByKey {
      case (seq, buffer) => { //seq序列当前周期中数量对集合，buffer表缓冲当中的值，所谓的checkPoint
        val sumCount = seq.sum + buffer.getOrElse(0L)
        Option(sumCount) //表往缓存里边更新对值　　它需要返回一个Option
      }
    }
  }
}
