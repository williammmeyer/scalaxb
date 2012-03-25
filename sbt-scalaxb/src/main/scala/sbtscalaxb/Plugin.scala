package sbtscalaxb

import sbt._
import scalaxb.{compiler => sc}
import scalaxb.compiler.{Config => ScConfig}

object Plugin extends sbt.Plugin {
  import Keys._
  import ScalaxbKeys._

  object ScalaxbKeys {
    lazy val scalaxb          = TaskKey[Seq[File]]("scalaxb")
    lazy val generate         = TaskKey[Seq[File]]("scalaxb-generate")
    lazy val scalaxbConfig    = SettingKey[ScConfig]("scalaxb-config")
    lazy val scalaxbConfig1   = SettingKey[Config1]("scalaxb-config1")
    lazy val scalaxbConfig2   = SettingKey[Config2]("scalaxb-config2")
    lazy val xsdSource        = SettingKey[File]("scalaxb-xsd-source")
    lazy val wsdlSource       = SettingKey[File]("scalaxb-wsdl-source")
    lazy val packageName      = SettingKey[String]("scalaxb-package-name")
    lazy val packageNames     = SettingKey[Map[URI, String]]("scalaxb-package-names")
    lazy val classPrefix      = SettingKey[Option[String]]("scalaxb-class-prefix")
    lazy val paramPrefix      = SettingKey[Option[String]]("scalaxb-param-prefix")
    lazy val attributePrefix  = SettingKey[Option[String]]("scalaxb-attribute-prefix")
    lazy val prependFamily    = SettingKey[Boolean]("scalaxb-prepend-family")
    lazy val wrapContents     = SettingKey[Seq[String]]("scalaxb-wrap-contents")
    lazy val contentsSizeLimit = SettingKey[Int]("scalaxb-contents-size-limit")
    lazy val chunkSize        = SettingKey[Int]("scalaxb-chunk-size")
    lazy val packageDir       = SettingKey[Boolean]("scalaxb-package-dir")
    lazy val generateRuntime  = SettingKey[Boolean]("scalaxb-generate-runtime")
    lazy val protocolFileName = SettingKey[String]("scalaxb-protocol-file-name")
    lazy val protocolPackageName  = SettingKey[Option[String]]("scalaxb-protocol-package-name")
    lazy val laxAny           = SettingKey[Boolean]("scalaxb-lax-any")
    lazy val combinedPackageNames = SettingKey[Map[Option[String], Option[String]]]("scalaxb-combined-package-names")
  }

  object ScalaxbCompile {
    def apply(sources: Seq[File], packageName: String, outdir: File): Seq[File] =
      apply(sources, sc.Config(packageNames = Map(None -> Some(packageName))), outdir, false)

    def apply(sources: Seq[File], config: sc.Config, outdir: File, verbose: Boolean = false): Seq[File] =
      sources.headOption map { src =>
        import sc._
        val module = Module.moduleByFileName(src, verbose)
        module.processFiles(sources, config.copy(outdir = outdir))
      } getOrElse {Nil}
  }

  lazy val scalaxbSettings: Seq[Project.Setting[_]] = inConfig(Compile)(baseScalaxbSettings)
  lazy val baseScalaxbSettings: Seq[Project.Setting[_]] = Seq(
    scalaxb <<= (generate in scalaxb).identity,
    generate in scalaxb <<= (sources in scalaxb, scalaxbConfig in scalaxb,
        sourceManaged in scalaxb, logLevel in scalaxb) map { (sources, config, outdir, ll) =>
      ScalaxbCompile(sources, config, outdir, ll == Level.Debug) },
    sourceManaged in scalaxb <<= sourceManaged.identity,
    sources in scalaxb <<= (xsdSource in scalaxb, wsdlSource in scalaxb) map { (xsd, wsdl) =>
      (wsdl ** "*.wsdl").get ++ (xsd ** "*.xsd").get },
    xsdSource in scalaxb <<= (sourceDirectory, configuration) { (src, config) =>
      if (Seq(Compile, Test) contains config) src / "xsd"
      else src / "main" / "xsd" },
    wsdlSource in scalaxb <<= (sourceDirectory, configuration) { (src, config) =>
      if (Seq(Compile, Test) contains config) src / "wsdl"
      else src / "main" / "wsdl" },
    clean in scalaxb <<= (sourceManaged in scalaxb) map { (outdir) =>
      IO.delete((outdir ** "*").get)
      IO.createDirectory(outdir) },
    packageName in scalaxb := "generated",
    packageNames in scalaxb := Map(),
    classPrefix in scalaxb := None,
    paramPrefix in scalaxb := None,
    attributePrefix in scalaxb := None,
    prependFamily in scalaxb := false,
    wrapContents in scalaxb := Nil,
    contentsSizeLimit in scalaxb := 20,
    chunkSize in scalaxb := 10,
    packageDir in scalaxb := true,
    generateRuntime in scalaxb := true,
    protocolFileName in scalaxb := sc.Defaults.protocolFileName,
    protocolPackageName in scalaxb := None,
    laxAny in scalaxb := false,
    combinedPackageNames in scalaxb <<= (packageName in scalaxb, packageNames in scalaxb) { (x, xs) =>
      (xs map { case (k, v) => ((Some(k.toString): Option[String]), Some(v)) }) updated (None, Some(x)) },
    scalaxbConfig1 in scalaxb <<= (combinedPackageNames in scalaxb,
        packageDir in scalaxb,
        classPrefix in scalaxb,
        paramPrefix in scalaxb,
        attributePrefix in scalaxb,
        prependFamily in scalaxb,
        wrapContents in scalaxb) { (pkg, pkgdir, cpre, ppre, apre, pf, w) =>
      Config1(packageNames = pkg,
        packageDir = pkgdir,
        classPrefix = cpre,
        paramPrefix = ppre,
        attributePrefix = apre,
        prependFamilyName = pf, 
        wrappedComplexTypes = w.toList) },
    scalaxbConfig2 in scalaxb <<= (contentsSizeLimit in scalaxb,
        generateRuntime in scalaxb,
        chunkSize in scalaxb,
        protocolFileName in scalaxb,
        protocolPackageName in scalaxb,
        laxAny in scalaxb) { (csl, rt, cs, pfn, ppn, la) =>
      Config2(contentsSizeLimit = csl,
        generateRuntime = rt,
        sequenceChunkSize = cs,
        protocolFileName = pfn,
        protocolPackageName = ppn,
        laxAny = la) },
    scalaxbConfig in scalaxb <<= (scalaxbConfig1 in scalaxb, scalaxbConfig2 in scalaxb) { (c1, c2) =>
      ScConfig(packageNames = c1.packageNames,
        packageDir = c1.packageDir,
        classPrefix = c1.classPrefix,
        paramPrefix = c1.paramPrefix,
        attributePrefix = c1.attributePrefix,
        prependFamilyName = c1.prependFamilyName,
        wrappedComplexTypes = c1.wrappedComplexTypes,
        contentsSizeLimit = c2.contentsSizeLimit,
        generateRuntime = c2.generateRuntime,
        sequenceChunkSize = c2.sequenceChunkSize,
        protocolFileName = c2.protocolFileName,
        protocolPackageName = c2.protocolPackageName,
        laxAny = c2.laxAny) },
    logLevel in scalaxb <<= logLevel?? Level.Info
  )
}

case class Config1(packageNames: Map[Option[String], Option[String]] = Map(None -> None),
  classPrefix: Option[String] = None,
  classPostfix: Option[String] = None,
  paramPrefix: Option[String] = None,
  attributePrefix: Option[String] = None,
  outdir: File = new File("."),
  packageDir: Boolean = false,
  wrappedComplexTypes: List[String] = Nil,
  prependFamilyName: Boolean = false) {}

case class Config2(seperateProtocol: Boolean = true,
  protocolFileName: String = "xmlprotocol.scala",
  protocolPackageName: Option[String] = None,
  defaultNamespace: Option[String] = None,
  generateRuntime: Boolean = true,
  contentsSizeLimit: Int = 20,
  sequenceChunkSize: Int = 10,
  laxAny: Boolean = false) {}
