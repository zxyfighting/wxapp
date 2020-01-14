package com.faw.hq.dmp.spark.imp.wxapp.contanst

import java.text.SimpleDateFormat
import java.util.Date

import com.faw.hq.dmp.spark.imp.wxapp.util.ConfigUtil
import org.apache.commons.configuration2.FileBasedConfiguration
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer

/**
  * @ program: wxapp
  * @ description
  * @ author: ZhangXiuYun
  * @ create: 2020-01-10 16:58
  **/
//定义常量
object  KafkaParams extends Enumeration {

    // sparkstreaming 消费 kafka 时的 Consumer 参数
    val config: FileBasedConfiguration = ConfigUtil("config.properties").config
    val brokerList = config.getString("kafka.broker.list")
    val topic = config.getString("kafka.topic")
    val topics = Array(topic)
    val module: String = "Test"
    //消费者组的名称
    val groupid = config.getString("group.id")
    //kafka参数
    val kafkaParams1 = Map[String, Object](
    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokerList,
    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
    ConsumerConfig.GROUP_ID_CONFIG -> groupid,
    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "latest",
    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> (false: java.lang.Boolean)
    )
    //hdfs参数
    val hdfs_url = config.getString("hdfs.url")
    val currentTime: Date = new Date()
    val formatter = new SimpleDateFormat("yyyy-MM-dd/");
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val dateString: String = dateFormat.format(new Date())
    val dateFormat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val dateString1: String = dateFormat1.format(new Date())
    val timeout = config.getString("redis.expireTime")
    //获取mysql配置
    val url = config.getString("jdbc.url")
    val userName = config.getString("jdbc.user")
    val password = config.getString("jdbc.password")
    //获取连接oneid地址，将数据推送到OneId平台
    val oneId = KafkaParams.config.getString("oneId.ip")


}
