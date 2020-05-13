(ns cf-up-and-running.components.credentials
  (:require [com.stuartsierra.component :as component]
            [cf-up-and-running.protocols.credentials :as protocols.credentials])
  (:import (software.amazon.awssdk.auth.credentials ProfileCredentialsProvider)))

(defrecord Credentials [profile-name credentials]
  component/Lifecycle
  (start [component]
    (if credentials
      component
      (assoc component :credentials (-> (ProfileCredentialsProvider/builder)
                                        (.profileName profile-name)
                                        .build))))

  (stop [component]
    (assoc component :credentials nil))

  protocols.credentials/Credentials
  (credentials [_]
    credentials))

(defn new-credentials
  ([] (new-credentials "default"))
  ([profile-name] (map->Credentials {:profile-name profile-name})))

(comment
  (def cred (component/start (new-credentials))))
