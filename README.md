## Overview

## Components

## Architecture

## NiFi Flowfile Attributes

## Example Mapping

The following is an example of a transition schema. It is here mainly to give an idea of what they look like.

```
{
  "subject": "A descriptive name that allows the user to associate the Schema to the Source Avro",
  "version": 1,
  "sourceSubject": "SocialMediaActivity",
  "sourceSchemaVersion": 1,
  "vertices": [
    {
      "label": "social_media_user",
      "graphNodeId": "left",
      "graphNodeClassName": "SocialMediaUser",
      "graphNodeClassVersion": 1,
      "properties": [
        {
          "graphClassPropertyName": "/Username",
          "sourceSchemaPropertyName": "/LeftUsername"
        },
        {
          "graphClassPropertyName": "/Service",
          "sourceSchemaPropertyName": "/LeftService",
          "defaultValue": "BigCorpChat"
        },
        {
          "graphClassPropertyName": "/IdentityVerified",
          "sourceSchemaPropertyName": "/LeftVerified",
          "defaultValue": false
        }
      ]
    },
    {
      "label": "social_media_user",
      "graphNodeId": "right",
      "graphNodeClassName": "SocialMediaUser",
      "graphNodeClassVersion": 1,
      "properties": [
        {
          "graphClassPropertyName": "/Username",
          "sourceSchemaPropertyName": "/RightUsername"
        },
        {
          "graphClassPropertyName": "/Service",
          "sourceSchemaPropertyName": "/RightService",
          "defaultValue": "BigCorpChat"
        },
        {
          "graphClassPropertyName": "/IdentityVerified",
          "sourceSchemaPropertyName": "/RightVerified",
          "defaultValue": false
        }
      ]
    }
  ],
  "edges": [
    {
      "fromGraphNodeId": "left",
      "toGraphNodeId": "right",
      "label": "interaction",
      "graphEdgeClassName": "SocialMediaInteraction",
      "graphEdgeClassVersion": 1,
      "properties": [
        {
          "graphClassPropertyName": "/Type",
          "sourceSchemaPropertyName": "/InteractionType"
        },
        {
          "graphClassPropertyName": "/Count",
          "sourceSchemaPropertyName": "/InteractionCount",
          "defaultValue": -1
        }
      ]
    }
  ]
}
```