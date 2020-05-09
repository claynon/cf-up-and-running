(ns user)

(import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider)
(import software.amazon.awssdk.regions.Region)
(import software.amazon.awssdk.services.s3.S3Client)
(import software.amazon.awssdk.services.ec2.Ec2Client)

(def credentials (-> (ProfileCredentialsProvider/builder)
                     (.profileName "default")
                     .build))

(def s3-client (-> (S3Client/builder)
                   (.region Region/US_EAST_2)
                   (.credentialsProvider credentials)
                   .build))

(.listBuckets s3-client)

(import java.util.Base64)
(import software.amazon.awssdk.regions.Region)
(import software.amazon.awssdk.services.ec2.Ec2Client)
(import software.amazon.awssdk.services.ec2.model.AuthorizeSecurityGroupIngressRequest)
(import software.amazon.awssdk.services.ec2.model.CreateSecurityGroupRequest)
(import software.amazon.awssdk.services.ec2.model.CreateTagsRequest)
(import software.amazon.awssdk.services.ec2.model.DeleteSecurityGroupRequest)
(import software.amazon.awssdk.services.ec2.model.InstanceType)
(import software.amazon.awssdk.services.ec2.model.IpPermission)
(import software.amazon.awssdk.services.ec2.model.IpRange)
(import software.amazon.awssdk.services.ec2.model.RunInstancesRequest)
(import software.amazon.awssdk.services.ec2.model.Tag)
(import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest)


(def ec2-client (-> (Ec2Client/builder)
                    (.region Region/US_EAST_2)
                    (.credentialsProvider credentials)
                    .build))

;; add security group
(def sg-name "java-sdk-example")

(def sg-create-request (-> (CreateSecurityGroupRequest/builder)
                           (.groupName sg-name)
                           (.description "sg of the example instance")
                           .build))

(def ip-range (-> (IpRange/builder)
                  (.cidrIp "0.0.0.0/0")
                  .build))

(def ip-permission (-> (IpPermission/builder)
                       (.ipProtocol "tcp")
                       (.toPort (int 8080))
                       (.fromPort (int 8080))
                       (.ipRanges [ip-range])
                       .build))

(def auth-request (-> (AuthorizeSecurityGroupIngressRequest/builder)
                      (.groupName sg-name)
                      (.ipPermissions [ip-permission])
                      .build))

(def security-group (.createSecurityGroup ec2-client sg-create-request))
(.authorizeSecurityGroupIngress ec2-client auth-request)

;; create EC2 instance
(def user-data
  "#!/bin/bash
   echo \"Hello, World\" > index.html
   nohup busybox httpd -f -p 8080 &")

(def ec2-run-request (-> (RunInstancesRequest/builder)
                     (.imageId "ami-0c55b159cbfafe1f0")
                     (.instanceType InstanceType/T2_MICRO)
                     (.maxCount (int 1))
                     (.minCount (int 1))
                     (.securityGroups [sg-name])
                     (.userData (.encodeToString (Base64/getEncoder) (.getBytes user-data)))
                     .build))

(def ec2-response (.runInstances ec2-client ec2-run-request))

(def instance-id
 (-> (.instances ec2-response)
     (.get 0)
     .instanceId))

;; add name tag
(def tag (-> (Tag/builder)
             (.key "Name")
             (.value "java-sdk-example")
             .build))

(def tag-request (-> (CreateTagsRequest/builder)
                     (.resources [instance-id])
                     (.tags [tag])
                     .build))
(.createTags ec2-client tag-request)

;; clean up
(def terminate-request (-> (TerminateInstancesRequest/builder)
                           (.instanceIds [instance-id])
                           .build))
(.terminateInstances ec2-client terminate-request)

(def delete-sg (-> (DeleteSecurityGroupRequest/builder)
                   (.groupId (.groupId security-group))
                   .build))
(.deleteSecurityGroup ec2-client delete-sg)
