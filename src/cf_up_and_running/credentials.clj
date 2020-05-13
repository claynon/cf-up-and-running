(ns cf-up-and-running.credentials
  (:require [com.stuartsierra.component :as component])
  (:import (software.amazon.awssdk.auth.credentials ProfileCredentialsProvider)))

(defprotocol CredentialsP
  (credentials [component]));;TODO extract

(defrecord Credentials [profile-name credentials]
  component/Lifecycle
  (start [component]
    (if credentials
      component
      (assoc component :credentials (-> (ProfileCredentialsProvider/builder)
                                       (.profileName profile-name)
                                       .build))))

  (stop [component]
    (if (not credentials)
      component
      (assoc component :credentials nil)))

  CredentialsP
  (credentials [_]
       credentials))

(defn new-credentials
  ([] (new-credentials "default"))
  ([profile-name] (map->Credentials {:profile-name profile-name})))

(comment
  (def cred (component/start (new-credentials))))
