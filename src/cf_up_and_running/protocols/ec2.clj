(ns cf-up-and-running.protocols.ec2)

(defprotocol Ec2Instance
  (create [component name sg-name user-data])
  (terminate [component instance-id]))
