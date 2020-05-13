(ns cf-up-and-running.protocols.security-group)

(defprotocol SecurityGroup
  (create-sg [component name])
  (terminate-sg [component name]))
