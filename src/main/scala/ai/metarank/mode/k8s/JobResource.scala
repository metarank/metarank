package ai.metarank.mode.k8s

import ai.metarank.config.MPath
import ai.metarank.mode.k8s.JobResource.{JobSpec, Metadata}
import io.circe.Codec

case class JobResource(
    apiVersion: String = "flink.apache.org/v1beta1",
    kind: String = "FlinkDeployment",
    metadata: Metadata,
    spec: JobSpec
)

object JobResource {
  object BootstrapJobResource {
    def apply(workdir: MPath, parallelism: Int): JobResource = JobResource(
      name = "bootstrap",
      workdir = workdir,
      savepoint = None,
      mode = "stateless",
      entryClass = "ai.metarank.mode.bootstrap.Bootstrap",
      parallelism = parallelism
    )
  }

  object UploadJobResource {
    def apply(workdir: MPath, parallelism: Int): JobResource = JobResource(
      name = "upload",
      workdir = workdir,
      savepoint = None,
      mode = "stateless",
      entryClass = "ai.metarank.mode.update.Update",
      parallelism = parallelism
    )
  }

  object UpdateJobResource {
    def apply(workdir: MPath, parallelism: Int): JobResource = JobResource(
      name = "update",
      workdir = workdir,
      savepoint = Some(workdir.child("savepoint")),
      mode = "savepoint",
      entryClass = "ai.metarank.mode.update.Update",
      parallelism = parallelism
    )
  }

  def apply(
      name: String,
      workdir: MPath,
      savepoint: Option[MPath],
      mode: String,
      entryClass: String,
      parallelism: Int
  ): JobResource =
    JobResource(
      metadata = Metadata(name = s"metarank-$name"),
      spec = JobSpec(
        taskManager = ManagerSpec(ResourceSpec(memory = "2048m", cpu = 1.0)),
        jobManager = ManagerSpec(ResourceSpec(memory = "2048m", cpu = 1.0)),
        podTemplate = PodTemplateSpec(
          metadata = Metadata(name = "pod-template"),
          spec = PodSpec(
            containers = List(
              ContainerSpec(
                name = "flink-main-container",
                volumeMounts = List(VolumeMountSpec(name = "config", mountPath = "/config/")),
                env = List(
                  EnvSpec(
                    name = "AWS_ACCESS_KEY_ID",
                    valueFrom = KeyRefSpec(SecretKeyRefSpec(name = "metarank-keys", key = "AWS_ACCESS_KEY_ID"))
                  ),
                  EnvSpec(
                    name = "AWS_SECRET_ACCESS_KEY",
                    valueFrom = KeyRefSpec(SecretKeyRefSpec(name = "metarank-keys", key = "AWS_SECRET_ACCESS_KEY"))
                  )
                )
              )
            ),
            volumes = List(VolumeSpec(name = "config", configMap = ConfigMapSpec(name = "metarank-config")))
          )
        ),
        flinkVersion = "v1_15",
        serviceAccount = "flink",
        image = "metarank/metarank@sha256:e126d4060fe82dae465d8e1cadd2114ba5136123fe71b3f9bd355b97d9fca542",
        job = JobConfigSpec(
          parallelism = parallelism,
          upgradeMode = mode,
          args = List("/config/config.yml"),
          jarURI = "local:///app/metarank.jar",
          entryClass = entryClass,
          state = "running",
          initialSavepointPath = savepoint.map(_.uri),
          allowNonRestoredState = true
        ),
        flinkConfiguration = FlinkConfigSpec(
          `high-availability.storageDir` = workdir.child("flink").child("ha").uri,
          `state.savepoints.dir` = workdir.child("flink").child("savepoints").uri,
          `taskmanager.numberOfTaskSlots` = "1",
          `high-availability` = "org.apache.flink.kubernetes.highavailability.KubernetesHaServicesFactory",
          `state.checkpoints.dir` = workdir.child("flink").child("checkpoints").uri
        )
      )
    )
  case class Metadata(name: String)
  case class JobSpec(
      taskManager: ManagerSpec,
      podTemplate: PodTemplateSpec,
      flinkVersion: String,
      serviceAccount: String,
      image: String,
      jobManager: ManagerSpec,
      job: JobConfigSpec,
      flinkConfiguration: FlinkConfigSpec
  )
  case class ManagerSpec(resource: ResourceSpec)
  case class ResourceSpec(memory: String, cpu: Double)
  case class PodTemplateSpec(
      apiVersion: String = "v1",
      kind: String = "Pod",
      metadata: Metadata,
      spec: PodSpec
  )
  case class PodSpec(containers: List[ContainerSpec], volumes: List[VolumeSpec])
  case class ContainerSpec(name: String, volumeMounts: List[VolumeMountSpec], env: List[EnvSpec])
  case class VolumeMountSpec(name: String, mountPath: String)
  case class EnvSpec(name: String, valueFrom: KeyRefSpec)
  case class KeyRefSpec(secretKeyRef: SecretKeyRefSpec)
  case class SecretKeyRefSpec(name: String, key: String)
  case class VolumeSpec(name: String, configMap: ConfigMapSpec)
  case class ConfigMapSpec(name: String)
  case class JobConfigSpec(
      parallelism: Int,
      upgradeMode: String,
      args: List[String],
      jarURI: String,
      entryClass: String,
      state: String,
      initialSavepointPath: Option[String],
      allowNonRestoredState: Boolean
  )
  case class FlinkConfigSpec(
      `high-availability.storageDir`: String,
      `state.savepoints.dir`: String,
      `taskmanager.numberOfTaskSlots`: String,
      `high-availability`: String,
      `state.checkpoints.dir`: String
  )

  import io.circe.generic.semiauto._

  implicit val flinkConfigSpecCodec: Codec[FlinkConfigSpec] = deriveCodec
  implicit val jobConfigSpecCodec: Codec[JobConfigSpec]     = deriveCodec
  implicit val configMapSpecCodec: Codec[ConfigMapSpec]     = deriveCodec
  implicit val volumeSpecCodec: Codec[VolumeSpec]           = deriveCodec
  implicit val secretKeyRefCodec: Codec[SecretKeyRefSpec]   = deriveCodec
  implicit val keyRefSpecCodec: Codec[KeyRefSpec]           = deriveCodec
  implicit val envSpecCodec: Codec[EnvSpec]                 = deriveCodec
  implicit val volumeMountSpecCodec: Codec[VolumeMountSpec] = deriveCodec
  implicit val containerSpecCodec: Codec[ContainerSpec]     = deriveCodec
  implicit val podSpecCodec: Codec[PodSpec]                 = deriveCodec
  implicit val podTemplateSpecCodec: Codec[PodTemplateSpec] = deriveCodec
  implicit val resourceSpecCodec: Codec[ResourceSpec]       = deriveCodec
  implicit val managerSpecCodec: Codec[ManagerSpec]         = deriveCodec
  implicit val jobSpecCodec: Codec[JobSpec]                 = deriveCodec
  implicit val metadataCodec: Codec[Metadata]               = deriveCodec
  implicit val jobResourceCodec: Codec[JobResource]         = deriveCodec
}
