from flask import Flask, request, jsonify
from transformers import pipeline
import logging


app = Flask(__name__)
app.debug = False
# FIX(SECURITY): removed hardcoded secret_key — Flask sessions are not used by this stateless moderation endpoint

classifier = pipeline("text-classification", model="unitary/toxic-bert")

@app.route('/api/v1/moderation/analyze', methods=['POST'])
def analyze_comment():
    data = request.get_json()
    comment = data.get("comment")
    if not comment:
        return jsonify({"error": "Missing comment"}), 400

    # FIX(DOS): bound input size to prevent OOM on the 2Gi-capped container
    if len(comment) > 2000:
        return jsonify({"error": "Comment too long, max 2000 characters"}), 400

    result = classifier(comment)[0]
    return jsonify({
        "label": result['label'],
        "score": result['score']

        #"is_hateful": result['label'] == "toxic" and result['score'] > 0.8
    })

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False, use_reloader=False)
