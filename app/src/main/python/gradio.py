import gradio as gr
import azure.cognitiveservices.speech as speechsdk
import os
import json

def transcribe_audio(audio_filepath):
    speech_config = speechsdk.SpeechConfig(subscription=os.environ.get('SPEECH_KEY'), region=os.environ.get('SPEECH_REGION'))
    speech_config.speech_recognition_language="zh-TW"
    audio_config = speechsdk.audio.AudioConfig(filename=audio_filepath)
    speech_recognizer = speechsdk.SpeechRecognizer(speech_config=speech_config, audio_config=audio_config)
    result = speech_recognizer.recognize_once()

    if result.reason == speechsdk.ResultReason.RecognizedSpeech:
        return result.text
    elif result.reason == speechsdk.ResultReason.NoMatch:
        return "No speech could be recognized."
    elif result.reason == speechsdk.ResultReason.Canceled:
        details = result.cancellation_details
        if details.reason == speechsdk.CancellationReason.Error:
            return f"Error: {details.error_details}"
        return "Speech recognition was canceled."
    return "Unexpected error occurred."

def load_question(questions, current_question_index):
    question_keys = list(questions.keys())
    if questions and current_question_index < len(question_keys):
        question_key = question_keys[current_question_index][1:]
        return questions[question_key]
    else:
        return "No more questions available"

def submit_answer(questions, current_question_index, audio):
    transcribed_text = transcribe_audio(audio)
    current_question_index += 1
    next_question = load_question(questions, current_question_index)
    return transcribed_text, next_question, current_question_index

def main():
    code = input("請輸入問卷編號: ")
    with open(f'{code}_questions.json', 'r', encoding='utf-8') as file:
        questions = json.load(file)

    current_question_index = 0

    with gr.Blocks() as demo:
        gr.Markdown("# ASR Questionnaire")
        question_label = gr.Label(value=load_question(questions, current_question_index))
        audio_input = gr.Audio(sources="microphone", type="filepath", label="Record your answer")
        transcribed_output = gr.Textbox(label="Transcription")
        submit_button = gr.Button("Submit")

        def update_output(audio):
            nonlocal current_question_index
            result, next_question, current_question_index = submit_answer(questions, current_question_index, audio)
            return result, next_question, None

        submit_button.click(fn=update_output, inputs=audio_input, outputs=[transcribed_output, question_label, audio_input])
        demo.launch(share=True)

