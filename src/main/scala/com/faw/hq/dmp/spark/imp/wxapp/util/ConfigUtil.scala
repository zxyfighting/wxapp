package com.faw.hq.dmp.spark.imp.wxapp.util

import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.FileBasedConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
/**
  * 获取配置文件信息连接
  *
  * */
object ConfigUtil {


  // FileBasedConfigurationBuilder:产生一个传入的类的实例对象
  // FileBasedConfiguration:融合FileBased与Configuration的接口
  // PropertiesConfiguration:从一个或者多个文件读取配置的标准配置加载器
  // configure():通过params实例初始化配置生成器
  // 向FileBasedConfigurationBuilder()中传入一个标准配置加载器类，生成一个加载器类的实例对象，然后通过params参数对其初始化


  def apply(propertiesName:String) = {
    val configurationUtil = new ConfigUtil()
    if (configurationUtil.config == null) {
      configurationUtil.config = new FileBasedConfigurationBuilder[FileBasedConfiguration](classOf[PropertiesConfiguration]).configure(new Parameters().properties().setFileName(propertiesName)).getConfiguration
    }
    configurationUtil
  }

  def main(args: Array[String]): Unit = {
    val config: FileBasedConfiguration = ConfigUtil("config.properties").config
    println(config.getString("jdbc.user"))
  }

}
class ConfigUtil(){
  var config:FileBasedConfiguration=null

}