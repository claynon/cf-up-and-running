(ns cf-up-and-running.protocols.cloud-formation)

(defprotocol CloudFormationStack
  (upsert [component stack-name template parameters-map])
  (delete [component stack-name])
  (outputs [component stack-name]))
