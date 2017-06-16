(ns editor.resource-node
  "Define the concept of a project, and its Project node type. This namespace bridges between Eclipse's workbench and
  ordinary paths."
  (:require [clojure.java.io :as io]
            [dynamo.graph :as g]
            [editor.core :as core]
            [editor.protobuf :as protobuf]
            [editor.resource :as resource]
            [util.text-util :as text-util]
            [editor.workspace :as workspace]
            [editor.outline :as outline]
            [util.digest :as digest])
  (:import [org.apache.commons.codec.digest DigestUtils]))

(set! *warn-on-reflection* true)

(def ^:private unknown-icon "icons/32/Icons_29-AT-Unknown.png")

(g/defnode ResourceNode
  (inherits core/Scope)
  (inherits outline/OutlineNode)
  (inherits resource/ResourceNode)

  (output save-data g/Any :cached (g/fnk [_node-id resource save-value dirty?]
                                    (let [write-fn (some-> resource
                                                     (resource/resource-type)
                                                     :write-fn)]
                                      (cond-> {:resource resource :dirty? dirty? :value save-value :node-id _node-id}
                                        (and write-fn save-value) (assoc :content (write-fn save-value))))))
  (output source-value g/Any :cached (g/fnk [resource]
                                       (when-let [read-fn (some-> resource
                                                            (resource/resource-type)
                                                            :read-fn)]
                                         (read-fn resource))))
  (output save-value g/Any (g/constantly nil))
  (output dirty? g/Bool (g/fnk [save-value source-value]
                          (and save-value (not= save-value source-value))))
  (output node-id+resource g/Any (g/fnk [_node-id resource] [_node-id resource]))
  (output build-targets g/Any (g/constantly []))
  (output node-outline outline/OutlineData :cached
    (g/fnk [_node-id resource source-outline child-outlines]
           (let [rt (resource/resource-type resource)
                 children (cond-> child-outlines
                            source-outline (into (:children source-outline)))]
             {:node-id _node-id
              :label (or (:label rt) (:ext rt) "unknown")
              :icon (or (:icon rt) unknown-icon)
              :children children})))

  (output sha256 g/Str :cached (g/fnk [resource save-data]
                                 (let [content (get save-data :content ::no-content)]
                                   (if (= ::no-content content)
                                     (with-open [s (io/input-stream resource)]
                                       (DigestUtils/sha256Hex ^java.io.InputStream s))
                                     (DigestUtils/sha256Hex ^String content))))))

(defn dirty? [node-id]
  (g/node-value node-id :dirty?))

(g/defnode PlaceholderResourceNode
  (inherits ResourceNode)

  (output build-targets g/Any (g/fnk [_node-id resource]
                                (g/error-fatal (format "Cannot build resource of type '%s'" (resource/ext resource)))))
  (output save-value g/Any (g/constantly nil)))

(defn register-ddf-resource-type [workspace & {:keys [ext node-type ddf-type load-fn icon view-types tags tag-opts label] :as args}]
  (let [args (assoc args
               :textual? true
               :load-fn (fn [project self resource]
                          (let [source-value (protobuf/read-text ddf-type resource)]
                            (load-fn project self resource source-value)))
               :read-fn (partial protobuf/read-text ddf-type)
               :write-fn (partial protobuf/map->str ddf-type))]
    (apply workspace/register-resource-type workspace (mapcat identity args))))

(g/defnode TextResourceNode
  (inherits ResourceNode)
  ;; TODO - modeled after script, rename to something less 'code'
  (property code g/Str)
  (output save-value g/Any (g/fnk [code] code)))

(defn register-text-resource-type [workspace & {:keys [ext node-type icon view-types tags tag-opts label] :as args}]
  (let [args (assoc args
               :textual? true
               :load-fn (fn [project self source-value] (g/set-property self :text-content source-value))
               :read-fn (comp text-util/crlf->lf slurp))]
    (apply workspace/register-resource-type workspace (mapcat identity args))))
