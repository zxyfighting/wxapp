package com.faw.hq.dmp.spark.imp.wxapp.realize

import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.spark.{ SparkConf, SparkContext}
import com.faw.hq.dmp.spark.imp.wxapp.util._
import org.apache.commons.lang3.StringUtils
import com.faw.hq.dmp.spark.imp.wxapp.contanst.KafkaParams
import com.faw.hq.dmp.spark.imp.wxapp.inter.{ImplJDBC, ImplPUV, Implkafka}
import org.apache.log4j.Logger

/**
  * @author ZhangXiuYun
  * @todo spark消费kafka，并将数据解析，修改数据格式，上传到hdfs上；将过滤的数据更新到oneId平台中
  */
object KafkaConsumer  {
  private val logger: Logger = Logger.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    while (true) {
      //配置spark上下文环境对象
      val processInterval = 59
      val conf = new SparkConf().setAppName("wxapp1").setMaster("local[*]")
      val sc = new SparkContext(conf)
      //设置采集器每十秒批次拉取数据
      val ssc = new StreamingContext(sc, Seconds(processInterval))

      /**
        * 参数获取
        */
      // Kafka 的 Offsets 以 module:groupId:topic 为 key 的 hash 结构存入 Redis 中
      val chkDir = KafkaParams.config.getString("hdfs.chk")
      ssc.sparkContext.setCheckpointDir(chkDir)

      // 初始化 Redis 连接池
      JedisPoolUtils.makePool(RedisConfig("prod.dbaas.private", 16359, 30000, "realtime123", 1000, 100, 50))

      //连接kafka
      val kafkaStream: InputDStream[ConsumerRecord[String, String]] = KafkaRedisUtils.createDirectStream(ssc, KafkaParams.kafkaParams1, KafkaParams.module, KafkaParams.groupid, KafkaParams.topics)

      //开始处理批次消息---将offset交予redis维护
      //将offset交给redis维护
      Implkafka.getOffset(KafkaParams.module,KafkaParams.groupid,kafkaStream)

      //解析json数据
      val resultDestream: DStream[String] = kafkaStream.map(_.value()).map(rdd => {
        val authInfo = GetMethods.getAuthInfo(rdd)
        authInfo
      })

      //若dataType为"auth"、"login"，且unionId和userId、phoneNo均不为空，
      //则调用oneid接口，将相应的数据送oneid平台
      val onidDestream: DStream[String] = resultDestream.filter(rdd => {
        val strings = rdd.split(";")
        strings(0).equals("auth") || strings(0).equals("login") && StringUtils.isNotBlank(strings(10)) &&
          StringUtils.isNotBlank(strings(13)) && StringUtils.isNoneBlank(strings(14))
      })
      //将处理的数据推送到OneId平台
      Implkafka.oneIdTo(onidDestream)

      /**
        *
        * 将数据解析，并根据时间进行追加到hdfs上
        *
        */
      resultDestream.foreachRDD(rdd => {
        Implkafka.toHDFS(rdd)
      })

      //由于resultDestream这个RDD用的频繁，减少数据计算量，用cache基于内存的存储数据，便于计算
      resultDestream.cache()

      //将数据结构改变为（pageUrl，1）
      val pvResult: DStream[(String, Int)] = resultDestream.map(rdd => {
        val strings = rdd.split(";")
        (strings(17), 1)
      }).filter(rdd => {
        !rdd._1.equals("\\N")
      })
      val pvResult1: DStream[(String, Int)] = pvResult.map(rdd => (KafkaParams.dateString, rdd._2))

      //将存在的pageURL累加---有状态计算
      val pvResultCounts: DStream[(String, Long)] = ImplPUV.pvUpdate(pvResult1)
      pvResultCounts.foreachRDD(rdd=>{
        rdd.foreach(println(_))
        println("----------")
      })
      pvResultCounts.cache()

      //法一 ：将数据保存在mysql数据库,连接MySQL是一个高消耗的事情，一个分区连接一次
      var sql="replace into dmp_behavior_wxapp_pv(wx_pv_dt,wx_pv_amounts,create_time,update_time) values(?,?,?,now())"
      ImplJDBC.puvCounts(sql,pvResultCounts)

      //uv是要去重
      //获取过期时间为一天
      //去重--根据今天访问过的用户清单进行过滤
      //1.过滤出uninoid为空的对象
      val mapRDD: DStream[PeopleVisit] = resultDestream.filter(rdd=>{
        val strings = rdd.split(";")
        !strings(10).equals("\\N")
      })
        .map(rdd => {
          val strings = rdd.split(";")
          PeopleVisit(strings(10), KafkaParams.dateString)
        })

      //2.redis过滤批次间重复的数据
      val filterRDD: DStream[PeopleVisit] =ImplPUV.filterRedis(mapRDD,ssc)

      //3.本批次去重
      val distinctDstream: DStream[PeopleVisit] = ImplPUV.groupByUnion(filterRDD)

      //4. 把所有今天访问过的用户保存起来
      ImplPUV.distinctRedis(distinctDstream)
       distinctDstream.foreachRDD(rdd=>{
          rdd.foreach(println(_))
        })

       //利用有状态计算算子，进行跨批次累加
       val updateDes1: DStream[(String, Long)] = distinctDstream.map(rdd => (rdd.dateToday, 1L))
      val uvDstream: DStream[(String, Long)] = ImplPUV.getUsers(updateDes1)

      // 法一 ：将数据保存在mysql数据库,连接MySQL是一个高消耗的事情，一个分区连接一次
      var sql1 = "replace into dmp_behavior_wxapp_uv(wx_uv_dt,wx_uv_amounts,create_time,update_time) values(?,?,?,now())"
      ImplJDBC.puvCounts(sql1,uvDstream)
      //开启采集器
      ssc.start()
      ssc.awaitTerminationOrTimeout(GetMethods.getSecondsNextEarlyMorning())
      ssc.stop(false, true)
    }
  }
  case class PeopleVisit(uninid: String, dateToday: String)

}