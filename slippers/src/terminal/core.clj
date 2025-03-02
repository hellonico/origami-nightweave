(ns terminal.core
  (:require [cljfx.api :as fx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [pyjama.core]
            [clojure.java.shell :refer [sh]]))


(def state (atom {:url "http://localhost:11434" :input "" :output ""}))

;; Get system information
(defn get-system-info []
  (let [os (System/getProperty "os.name")
        user (System/getProperty "user.name")
        home (System/getProperty "user.home")
        temp (System/getProperty "java.io.tmpdir")
        ip (-> (sh "hostname" "-I")
               :out
               (str/trim))
        java-version (System/getProperty "java.version")]
    {:os os
     :user user
     :home home
     :temp temp
     :ip ip
     :java-version java-version}))

(defn model-call [input]
  (let [system-info (get-system-info)
        pre (format "You are on %s. The current user is %s. The home folder is %s. The temp folder is %s. The IP address is %s. The Java version is %s. Convert this human input to a bash command:
%%s
. Only output the bash code, nothing else."
                    (:os system-info)
                    (:user system-info)
                    (:home system-info)
                    (:temp system-info)
                    (:ip system-info)
                    (:java-version system-info))]
    (pyjama.core/ollama (:url @state)
                        :generate
                        {:pre pre
                         :prompt input}
                        :response)))

(defn execute-command [command]
  (let [result (sh "bash" "-c" command)]
    (if (= 0 (:exit result))
      (:out result)
      (str "Error: " (:err result)))))

(defn root-view [{:keys [input output]}]
  {:fx/type          :stage
   :showing          true
   :on-close-request (fn [_] (System/exit 0))
   :title            "Terminal App"
   :scene            {:fx/type :scene
                      :stylesheets #{(.toExternalForm (io/resource "terminal.css"))}
                      :root {:fx/type  :v-box
                             :children [{:fx/type   :text-area
                                         :editable  false
                                         :text      output
                                         :wrap-text true}
                                        {:fx/type         :text-area
                                         :prompt-text     "Enter your command here"
                                         :text            input
                                         :on-text-changed (fn [event]
                                                            (swap! state assoc :input event))
                                         }
                                        {:fx/type   :button
                                         :text      "Execute"
                                         :on-action (fn [_]
                                                      (let [command (model-call input)
                                                            result (execute-command command)
                                                            ]
                                                        (swap! state assoc :output result)))
                                         }
                                        ]}}})

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type root-view)))

(defn -main [& _]
  (fx/mount-renderer state renderer))