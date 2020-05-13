(ns cf-up-and-running.protocols.cloud-formation
  (:refer-clojure :exclude [update]))

(defprotocol CloudFormationStack
  (create [component stack-name template]) ;; TODO upsert
  (update [component stack-name template])
  (delete [component stack-name])
  (outputs [component stack-name]))
